package com.frameflow.service;

import static com.frameflow.common.Values.*;

import com.frameflow.common.*;
import com.frameflow.mapper.StudioMapper;
import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AssetService {
  private final StudioMapper db;
  private final StudioService studio;
  private final AuthService auth;
  private final AssetMediaService media;
  private final Path root;

  public AssetService(
      StudioMapper db,
      StudioService studio,
      AuthService auth,
      AssetMediaService media,
      @Value("${app.upload-dir}") String path) {
    this.db = db;
    this.studio = studio;
    this.auth = auth;
    this.media = media;
    this.root = Path.of(path).toAbsolutePath().normalize();
  }

  @Transactional(rollbackFor = Exception.class)
  public long upload(MultipartFile file, Map<String, String> fields) throws IOException {
    Actor.current().require("ADMIN", "CREATOR");
    if (file.isEmpty()) throw BusinessException.bad("请选择非空文件");
    var row = new HashMap<String, Object>(fields);
    row.put("creatorId", parseCreator(fields.get("creatorId")));
    row.put("status", "ACTIVE");
    validate(row);
    String original = Optional.ofNullable(file.getOriginalFilename()).orElse("asset");
    original = original.replace('\\', '/');
    original = original.substring(original.lastIndexOf('/') + 1);
    String suffix =
        original.contains(".") ? original.substring(original.lastIndexOf('.')).toLowerCase() : "";
    if (suffix.length() > 15) suffix = "";
    String relative = "assets/" + LocalDate.now() + "/" + UUID.randomUUID() + suffix;
    Path target = root.resolve(relative);
    Files.createDirectories(target.getParent());
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
      @Override
      public void afterCompletion(int status) {
        if (status != STATUS_COMMITTED) {
          try {
            Files.deleteIfExists(target);
          } catch (IOException ex) {
            org.slf4j.LoggerFactory.getLogger(AssetService.class).warn("清理未完成上传失败：{}", target, ex);
          }
          if (row.get("id") != null) media.discard(id(row.get("id")));
        }
      }
    });
    file.transferTo(target);
    row.put("originalFilename", original);
    row.put("storagePath", relative);
    row.put(
        "mimeType", Optional.ofNullable(file.getContentType()).orElse("application/octet-stream"));
    row.put("sizeBytes", file.getSize());
    row.put("actorId", Actor.current().id());
    db.insertAsset(row);
    long id = id(row.get("id"));
    if ("VIDEO".equals(row.get("kind"))) media.prepare(id, target);
    platforms(id, row);
    tags(id, String.valueOf(row.getOrDefault("tags", "")));
    auth.audit("UPLOAD", "ASSET", id, String.valueOf(row.get("name")));
    return id;
  }

  @Transactional
  public void update(long assetId, Map<String, Object> input) {
    Actor.current().require("ADMIN", "CREATOR");
    required(db.lockAsset(assetId), "素材");
    var row = studio.assetWriteAccess(assetId);
    row.putAll(input);
    row.put("id", assetId);
    row.put("creatorId", parseCreator(String.valueOf(row.get("creatorId"))));
    validate(row);
    db.updateAsset(row);
    platforms(assetId, row);
    tags(assetId, String.valueOf(row.getOrDefault("tags", "")));
    auth.audit("UPDATE", "ASSET", assetId, String.valueOf(row.get("name")));
  }

  private long parseCreator(String value) {
    try {
      return Long.parseLong(value);
    } catch (Exception ex) {
      throw BusinessException.bad("请选择素材所属创作者");
    }
  }

  private void validate(Map<String, Object> row) {
    studio.creatorWriteAccess(id(row.get("creatorId")));
    required(db.creator(id(row.get("creatorId"))), "创作者");
    if (row.get("name") == null || row.get("name").toString().isBlank())
      throw BusinessException.bad("请输入素材名称");
    oneOf((String) row.get("kind"), "VIDEO", "IMAGE", "AUDIO", "DOCUMENT");
    oneOf((String) row.get("licenseType"), "ORIGINAL", "LICENSED");
    oneOf((String) row.get("status"), "ACTIVE", "ARCHIVED");
    for (String key : List.of("licenseStart", "licenseEnd")) {
      if (row.get(key) != null && row.get(key).toString().isBlank()) row.put(key, null);
      if (row.get(key) != null) {
        try {
          LocalDate.parse(row.get(key).toString());
        } catch (Exception ex) {
          throw BusinessException.bad("授权日期格式错误");
        }
      }
    }
    if (row.get("licenseStart") == null) throw BusinessException.bad("请选择授权开始日期");
    if ("LICENSED".equals(row.get("licenseType"))
        && (row.get("licenseStart") == null || row.get("licenseEnd") == null))
      throw BusinessException.bad("授权素材需要填写完整授权期限");
    if (row.get("licenseStart") != null
        && row.get("licenseEnd") != null
        && LocalDate.parse(row.get("licenseEnd").toString())
            .isBefore(LocalDate.parse(row.get("licenseStart").toString())))
      throw BusinessException.bad("授权结束日期应不早于开始日期");
    String allowed = String.valueOf(row.getOrDefault("allowedPlatforms", "ALL"));
    if (allowed.isBlank()) allowed = "ALL";
    var codes = new LinkedHashSet<String>();
    for (String code : allowed.split(",")) {
      code = code.trim().toUpperCase();
      String selected = code;
      if (!"ALL".equals(code) && db.platforms().stream().noneMatch(platform -> selected.equals(platform.get("code"))))
        throw BusinessException.bad("不支持的授权平台：" + code);
      codes.add(code);
    }
    row.put("allowedPlatforms", String.join(",", codes));
    row.put("allPlatforms", codes.contains("ALL"));
  }

  private void platforms(long assetId, Map<String, Object> row) {
    db.deleteAssetPlatforms(assetId);
    if (!Boolean.TRUE.equals(row.get("allPlatforms")))
      for (String code : row.get("allowedPlatforms").toString().split(","))
        db.addAssetPlatform(assetId, code);
  }

  private void tags(long assetId, String text) {
    db.deleteTags(assetId);
    for (String name : text.split(",")) {
      name = name.trim();
      if (!name.isEmpty() && !"null".equals(name)) {
        db.insertTag(name);
        db.addAssetTag(assetId, name);
      }
    }
  }

  public Path file(long assetId) {
    var row = studio.assetAccess(assetId);
    Path path = root.resolve(row.get("storagePath").toString()).normalize();
    if (!path.startsWith(root) || !Files.isRegularFile(path))
      throw new BusinessException(404, "素材文件不存在");
    return path;
  }

  public Map<String, Object> details(long assetId) {
    return studio.assetAccess(assetId);
  }
}
