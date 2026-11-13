package com.frameflow.service;

import static com.frameflow.common.Values.*;

import com.frameflow.common.*;
import com.frameflow.integration.*;
import com.frameflow.mapper.*;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class PlatformIntegrationService {
  public static final List<String> CODES = List.of("BILIBILI", "DOUYIN", "KUAISHOU", "XIAOHONGSHU", "YOUTUBE", "INSTAGRAM");
  private final PlatformIntegrationMapper db;
  private final AuthMapper users;
  private final StudioMapper studio;
  private final PlatformSettings settings;
  private final PlatformTokenCipher cipher;
  private final TransactionTemplate transaction;
  private final Map<String, PlatformProvider> providers = new HashMap<>();
  private final ConcurrentHashMap<String, Object> accountLocks = new ConcurrentHashMap<>();
  private final SecureRandom random = new SecureRandom();

  public PlatformIntegrationService(PlatformIntegrationMapper db, AuthMapper users, StudioMapper studio,
      PlatformSettings settings, PlatformTokenCipher cipher, TransactionTemplate transaction, List<PlatformProvider> adapters) {
    this.db = db; this.users = users; this.studio = studio; this.settings = settings; this.cipher = cipher; this.transaction = transaction;
    adapters.forEach(provider -> providers.put(provider.code(), provider));
  }

  public List<Map<String, Object>> integrations() {
    var result = new ArrayList<Map<String, Object>>();
    for (var platform : db.platforms()) {
      String code = platform.get("code").toString();
      if (!CODES.contains(code)) continue;
      boolean supported = providers.containsKey(code);
      boolean configured = supported && settings.clientConfigured(code) && cipher.configured() && settings.redirectValid(code);
      var row = new LinkedHashMap<String, Object>();
      row.put("code", code); row.put("name", platform.get("name"));
      row.put("status", !supported ? "APPROVAL_REQUIRED" : configured ? "READY" : "NOT_CONFIGURED");
      row.put("configured", configured); row.put("canConnect", configured);
      row.put("description", description(code)); row.put("requirements", requirements(code));
      row.put("docsUrl", docsUrl(code)); row.put("redirectUri", settings.redirectUri(code));
      result.add(row);
    }
    return result;
  }

  private String description(String code) {
    return switch (code) {
      case "YOUTUBE" -> "通过 Google 授权读取本人频道和已上传视频；完成应用配置后可关联同步。";
      case "INSTAGRAM" -> "通过 Instagram Login 关联本人 Business 或 Creator 专业账号，读取内容与可用指标。";
      case "DOUYIN" -> "网站应用需先完成开放平台主体与能力审核，再按获批的本人作品接口接入。";
      case "BILIBILI" -> "先申请哔哩哔哩开放平台应用及本人投稿读取权限，取得接口资料后启用连接。";
      case "KUAISHOU" -> "先申请快手开放平台应用和作品读取能力，取得获批接口资料后启用连接。";
      default -> "先确认小红书开放平台提供的账号授权与本人内容读取能力，取得授权资质后接入。";
    };
  }
  private List<String> requirements(String code) {
    if ("YOUTUBE".equals(code)) return List.of("Google Cloud 启用 YouTube Data API v3", "OAuth Web 应用客户端与精确回调地址", "测试账号加入应用测试用户；Testing 授权通常七天", "配置客户端凭据及 PLATFORM_TOKEN_KEY");
    if ("INSTAGRAM".equals(code)) return List.of("Instagram Business 或 Creator 专业账号", "Meta Business 类型应用，选择 Instagram Login", "HTTPS 回调与 instagram_business_basic 权限", "本人账号加入应用；服务其他账号需应用审核及业务验证", "配置客户端凭据及 PLATFORM_TOKEN_KEY");
    if ("DOUYIN".equals(code)) return List.of("企业或个体主体的网站应用资质", "HTTPS OAuth 回调", "获批的账号与本人作品读取权限及官方接口资料");
    return List.of("平台开发者应用审核", "获批的账号 OAuth 与本人内容读取权限", "官方客户端凭据、回调要求和接口资料");
  }
  private String docsUrl(String code) {
    return switch (code) {
      case "YOUTUBE" -> "https://developers.google.com/youtube/v3/docs/playlistItems/list";
      case "INSTAGRAM" -> "https://developers.facebook.com/docs/instagram-platform/instagram-api-with-instagram-login/";
      case "BILIBILI" -> "https://open.bilibili.com/";
      case "DOUYIN" -> "https://developer.open-douyin.com/";
      case "KUAISHOU" -> "https://open.kuaishou.com/";
      default -> "https://open.xiaohongshu.com/";
    };
  }

  private PlatformProvider ready(String code) {
    if (!CODES.contains(code)) throw BusinessException.bad("不支持的平台");
    var provider = providers.get(code);
    if (provider == null) throw BusinessException.conflict("该平台需要先申请应用和内容读取权限，当前尚未启用授权接入");
    cipher.requireKey();
    if (!settings.clientConfigured(code)) throw BusinessException.conflict("该平台尚未配置客户端凭据，请先完成开放平台申请");
    if (!settings.redirectValid(code)) throw BusinessException.conflict("请配置平台认可的回调地址；Instagram 需要 HTTPS");
    return provider;
  }
  private String randomToken() {
    byte[] bytes = new byte[48]; random.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }
  private void owner(Actor actor, long creatorId) {
    actor.require("ADMIN", "CREATOR");
    if (!actor.has("ADMIN") && !Objects.equals(actor.creatorId(), creatorId))
      throw new BusinessException(403, "仅可关联和同步本人的平台账号");
  }
  private Map<String, Object> ownScope() {
    Actor actor = Actor.current();
    var scope = new HashMap<String, Object>();
    if (!actor.has("ADMIN")) scope.put("creatorId", actor.has("CREATOR") && actor.creatorId() != null ? actor.creatorId() : -1L);
    return scope;
  }
  private Object accountLock(String code, String externalId) { return accountLocks.computeIfAbsent(code + ":" + externalId, ignored -> new Object()); }

  public Map<String, String> authorize(String code, Long requestedCreatorId) {
    Actor actor = Actor.current(); actor.require("ADMIN", "CREATOR");
    Long creatorId = requestedCreatorId != null ? requestedCreatorId : actor.creatorId();
    if (creatorId == null) throw BusinessException.bad("请选择要关联的创作者");
    owner(actor, creatorId);
    var creator = required(studio.creator(creatorId), "创作者");
    if (!"ACTIVE".equals(creator.get("status"))) throw BusinessException.conflict("创作者档案已停用");
    var provider = ready(code);
    var platform = required(db.platform(code), "平台");
    String state = randomToken(), verifier = randomToken();
    db.insertAttempt(Map.of("state", state, "platformId", platform.get("id"), "userId", actor.id(), "creatorId", creatorId, "codeVerifier", verifier));
    return Map.of("authorizationUrl", provider.authorizationUrl(state, verifier));
  }

  public String callback(String platformCode, String state, String authorizationCode, String error) {
    var platform = required(db.platform(platformCode), "平台");
    if (state == null || state.isBlank()) throw BusinessException.bad("授权状态缺失，请从平台账号页重新发起关联");
    var attempt = db.attempt(state);
    if (attempt == null || db.consumeAttempt(state, id(platform.get("id"))) != 1)
      throw BusinessException.bad("授权状态已失效或已使用，请重新发起关联");
    if (error != null && !error.isBlank()) throw BusinessException.bad("平台授权已取消或未获批准，请重新关联");
    if (authorizationCode == null || authorizationCode.isBlank()) throw BusinessException.bad("平台未返回授权码，请重新关联");
    var user = required(users.userById(id(attempt.get("userId"))), "授权用户");
    if (!"ACTIVE".equals(user.get("status"))) throw new BusinessException(403, "发起授权的账号已停用");
    long userId = id(user.get("id")), creatorId = id(attempt.get("creatorId"));
    var actor = new Actor(userId, user.get("username").toString(), user.get("displayName").toString(), users.roles(userId), user.get("creatorId") == null ? null : id(user.get("creatorId")));
    owner(actor, creatorId);
    var creator = required(studio.creator(creatorId), "创作者");
    if (!"ACTIVE".equals(creator.get("status"))) throw BusinessException.conflict("创作者档案已停用");
    var provider = ready(platformCode);
    var token = provider.exchange(authorizationCode, attempt.get("codeVerifier").toString());
    var profile = provider.profile(token);
    long connectionId = bind(platformCode, id(platform.get("id")), creatorId, actor.id(), profile, token);
    try {
      var result = sync(connectionId, actor);
      return "账号关联成功，已同步 " + result.get("syncedCount") + " 条作品";
    } catch (BusinessException ex) {
      return "账号已关联，首次同步未完成：" + ex.getMessage() + "。可在账号卡片重试同步。";
    }
  }

  private long bind(String platformCode, long platformId, long creatorId, long userId, PlatformProvider.Profile profile, PlatformProvider.Token token) {
    synchronized (accountLock(platformCode, profile.externalId())) {
      return transaction.execute(status -> {
      db.lockPlatform(platformId);
      var account = db.accountByExternal(platformId, profile.externalId());
      long accountId;
      if (account != null) {
        if (id(account.get("creatorId")) != creatorId)
          throw BusinessException.conflict("该平台账号已属于其他创作者，请由管理员核对归属");
        accountId = id(account.get("id"));
      } else {
        var row = new HashMap<String, Object>();
        row.put("platformId", platformId); row.put("creatorId", creatorId);
        row.put("name", shortened(profile.name(), 100)); row.put("externalId", profile.externalId());
        db.insertAccount(row); accountId = id(row.get("id"));
      }
        db.updateAccountName(accountId, shortened(profile.name(), 100));
        var row = tokenData(token); row.put("accountId", accountId); row.put("authorizedBy", userId);
        db.saveConnection(row);
        long connectionId = id(db.connectionByAccount(accountId).get("id"));
        users.audit(userId, "CONNECT_PLATFORM", "PLATFORM_ACCOUNT", accountId, "关联本人平台账号");
        return connectionId;
      });
    }
  }

  public List<Map<String, Object>> connections() {
    var rows = db.connections(ownScope());
    for (var row : rows) {
      if (expired(row) && !"DISCONNECTED".equals(row.get("status"))) row.put("status", "EXPIRED");
      row.put("canManage", true);
      for (String field : List.of("externalId", "authorizedBy", "tokenExpiresAt", "refreshExpiresAt", "refreshAvailable")) row.remove(field);
    }
    return rows;
  }
  private boolean expired(Map<String, Object> row) {
    LocalDateTime expires = dateTime(row.get("tokenExpiresAt"));
    LocalDateTime refreshExpires = dateTime(row.get("refreshExpiresAt"));
    boolean hasRefresh = Boolean.TRUE.equals(row.get("refreshAvailable")) || (row.get("refreshAvailable") instanceof Number value && value.intValue() != 0);
    return expires != null && !expires.isAfter(LocalDateTime.now())
        && ("INSTAGRAM".equals(row.get("platformCode")) || !hasRefresh || (refreshExpires != null && !refreshExpires.isAfter(LocalDateTime.now())));
  }
  public Map<String, Object> sync(long connectionId) { return sync(connectionId, Actor.current()); }
  private Map<String, Object> sync(long connectionId, Actor actor) {
    var initial = required(db.connection(connectionId), "平台连接"); owner(actor, id(initial.get("creatorId")));
    synchronized (accountLock(initial.get("platformCode").toString(), initial.get("externalId").toString())) {
      var connection = required(db.connection(connectionId), "平台连接"); owner(actor, id(connection.get("creatorId")));
      if ("DISCONNECTED".equals(connection.get("status"))) throw BusinessException.conflict("请先重新关联平台账号");
      var provider = ready(connection.get("platformCode").toString());
      var job = new HashMap<String, Object>(); job.put("connectionId", connectionId); job.put("triggeredBy", actor.id());
      db.insertJob(job);
      var identifiers = new HashSet<String>();
      try {
        if (expired(connection) || "EXPIRED".equals(connection.get("status")))
          throw new PlatformRemoteException("平台授权已失效，请重新关联账号", true);
        var token = new PlatformProvider.Token(cipher.decrypt((String) connection.get("accessToken")), cipher.decrypt((String) connection.get("refreshToken")),
            dateTime(connection.get("tokenExpiresAt")), dateTime(connection.get("refreshExpiresAt")), (String) connection.get("grantedScopes"));
        String cursor = null; var cursors = new HashSet<String>();
        do {
          if (provider.needsRefresh(token)) {
            token = provider.refresh(token);
            var refreshed = tokenData(token); refreshed.put("id", connectionId); db.updateToken(refreshed);
          }
          var page = provider.content(token, connection.get("externalId").toString(), cursor);
          var pageRows = new LinkedHashMap<String, PlatformProvider.Content>();
          for (var content : page.items()) pageRows.put(content.externalId(), content);
          transaction.executeWithoutResult(status -> {
            for (var content : pageRows.values()) db.upsertContent(contentData(connectionId, content));
          });
          identifiers.addAll(pageRows.keySet());
          cursor = page.nextCursor();
          if (cursor != null && cursor.isBlank()) cursor = null;
          if (cursor != null && !cursors.add(cursor)) throw new PlatformRemoteException("平台返回了重复分页，请稍后重试同步", false);
        } while (cursor != null);
        job.put("status", "SUCCEEDED"); job.put("syncedCount", identifiers.size()); job.put("errorMessage", null);
        transaction.executeWithoutResult(status -> {
          db.synced(connectionId); db.finishJob(job);
          users.audit(actor.id(), "SYNC_PLATFORM", "PLATFORM_ACCOUNT", id(connection.get("accountId")), "同步平台内容 " + identifiers.size() + " 条");
        });
        return Map.of("syncedCount", identifiers.size(), "lastSyncedAt", db.connection(connectionId).get("lastSyncedAt"));
      } catch (Exception ex) {
        String message = ex instanceof BusinessException ? ex.getMessage() : "平台同步未完成，请稍后重试或联系管理员";
        boolean tokenExpired = ex instanceof PlatformRemoteException remote && remote.expired();
        job.put("status", "FAILED"); job.put("syncedCount", identifiers.size()); job.put("errorMessage", shortened(message, 1000));
        db.finishJob(job); db.connectionError(connectionId, tokenExpired ? "EXPIRED" : "ERROR", shortened(message, 1000));
        if (ex instanceof BusinessException business) throw business;
        throw new BusinessException(502, message);
      }
    }
  }

  public void disconnect(long connectionId) {
    var initial = required(db.connection(connectionId), "平台连接"); owner(Actor.current(), id(initial.get("creatorId")));
    synchronized (accountLock(initial.get("platformCode").toString(), initial.get("externalId").toString())) {
      var connection = required(db.connection(connectionId), "平台连接"); owner(Actor.current(), id(connection.get("creatorId")));
      transaction.executeWithoutResult(status -> {
        db.disconnect(connectionId);
        users.audit(Actor.current().id(), "DISCONNECT_PLATFORM", "PLATFORM_ACCOUNT", id(connection.get("accountId")), "解除本地连接并删除保存的授权令牌，保留同步作品");
      });
    }
  }

  public Map<String, Object> content(Map<String, String> input) {
    var query = ownScope();
    int page = positive(input.getOrDefault("page", "1")), size = Math.min(positive(input.getOrDefault("pageSize", "20")), 100);
    String platform = input.get("platformCode"), search = input.get("q"), connectionId = input.get("connectionId");
    if (platform != null && !platform.isBlank()) { oneOf(platform, CODES.toArray(String[]::new)); query.put("platformCode", platform); }
    if (search != null && !search.isBlank()) query.put("q", search.trim());
    if (connectionId != null && !connectionId.isBlank()) {
      long selected;
      try { selected = Long.parseLong(connectionId); } catch (NumberFormatException ex) { throw BusinessException.bad("连接编号格式错误"); }
      var connection = required(db.connection(selected), "平台连接"); owner(Actor.current(), id(connection.get("creatorId")));
      query.put("connectionId", selected);
    }
    query.put("limit", size); query.put("offset", (long) (page - 1) * size);
    return Map.of("items", db.content(query), "total", db.countContent(query), "page", page, "pageSize", size);
  }
  private int positive(String value) {
    try { int number = Integer.parseInt(value); if (number > 0) return number; }
    catch (NumberFormatException ignored) {}
    throw BusinessException.bad("页码和每页数量须为正整数");
  }
  private Map<String, Object> tokenData(PlatformProvider.Token token) {
    var row = new HashMap<String, Object>(); row.put("accessToken", cipher.encrypt(token.accessToken())); row.put("refreshToken", cipher.encrypt(token.refreshToken()));
    row.put("tokenExpiresAt", token.expiresAt()); row.put("refreshExpiresAt", token.refreshExpiresAt()); row.put("grantedScopes", token.scopes()); return row;
  }
  private Map<String, Object> contentData(long connectionId, PlatformProvider.Content content) {
    var row = new HashMap<String, Object>(); row.put("connectionId", connectionId); row.put("externalId", content.externalId());
    row.put("title", shortened(content.title(), 500)); row.put("description", content.description()); row.put("coverUrl", content.coverUrl());
    row.put("permalink", content.permalink()); row.put("mediaType", content.mediaType()); row.put("publishedAt", content.publishedAt());
    row.put("viewCount", content.viewCount()); row.put("likeCount", content.likeCount()); row.put("commentCount", content.commentCount()); return row;
  }
  private String shortened(String value, int length) { return value != null && value.length() > length ? value.substring(0, length) : value; }
  private LocalDateTime dateTime(Object value) {
    if (value == null) return null;
    if (value instanceof LocalDateTime date) return date;
    if (value instanceof Timestamp timestamp) return timestamp.toLocalDateTime();
    return LocalDateTime.parse(value.toString().replace(' ', 'T'));
  }
}
