package com.frameflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.frameflow.common.BusinessException;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AssetMediaService {
  private static final Logger log = LoggerFactory.getLogger(AssetMediaService.class);
  private final StudioService studio;
  private final ObjectMapper json;
  private final Path root;

  public AssetMediaService(StudioService studio, ObjectMapper json,
      @Value("${app.upload-dir}") String uploadDir) {
    this.studio = studio;
    this.json = json;
    this.root = Path.of(uploadDir).toAbsolutePath().normalize();
  }

  public Map<String, Object> metadata(long assetId) {
    var asset = studio.assetAccess(assetId);
    String type = asset.get("kind").toString();
    var result = new LinkedHashMap<String, Object>();
    result.put("type", type);
    result.put("mimeType", asset.get("mimeType"));
    result.put("durationSeconds", null);
    result.put("posterUrl", null);
    result.put("previewUrl", null);
    if ("IMAGE".equals(type)) {
      source(asset);
      result.put("posterUrl", "/assets/" + assetId + "/file");
    } else if ("VIDEO".equals(type)) {
      result.put("durationSeconds", prepare(assetId, source(asset)));
      result.put("posterUrl", "/assets/" + assetId + "/poster");
      result.put("previewUrl", "/assets/" + assetId + "/preview");
    }
    return result;
  }

  public Path poster(long assetId) {
    return videoFile(assetId, "poster.jpg");
  }

  public Path preview(long assetId) {
    return videoFile(assetId, "preview.mp4");
  }

  private Path videoFile(long assetId, String name) {
    var asset = studio.assetAccess(assetId);
    if (!"VIDEO".equals(asset.get("kind"))) throw BusinessException.bad("该素材不是视频");
    prepare(assetId, source(asset));
    return directory(assetId).resolve(name);
  }

  private Path source(Map<String, Object> asset) {
    Path path = root.resolve(asset.get("storagePath").toString()).normalize();
    if (!path.startsWith(root) || !Files.isRegularFile(path))
      throw new BusinessException(404, "素材文件不存在");
    return path;
  }

  private Path directory(long assetId) {
    return root.resolve("previews").resolve(Long.toString(assetId));
  }

  // 缓存读取无需等待；仅生成过程串行，完成后原子发布目录。
  double prepare(long assetId, Path source) {
    Path destination = directory(assetId);
    Path staging = null;
    try {
      if (Files.isRegularFile(destination.resolve("metadata.json")))
        return json.readTree(destination.resolve("metadata.json").toFile()).get("durationSeconds").asDouble();
      synchronized (this) {
        if (Files.isRegularFile(destination.resolve("metadata.json")))
          return json.readTree(destination.resolve("metadata.json").toFile()).get("durationSeconds").asDouble();
        Files.createDirectories(destination.getParent());
        staging = Files.createTempDirectory(destination.getParent(), assetId + "-");
        double duration = probe(source, staging.resolve("source-probe.json"));
        Path preview = staging.resolve("preview.mp4");
        String filter = "[0:v:0]fps=30,scale=w='min(640,iw)':h='min(640,ih)':"
            + "force_original_aspect_ratio=decrease:force_divisible_by=2,setsar=1,format=yuv420p,split=2[body][head];"
            + "[body]trim=start=0.15:end=3.15,setpts=PTS-STARTPTS[bodytrim];"
            + "[head]trim=start=0:end=0.15,setpts=PTS-STARTPTS[headtrim];"
            + "[bodytrim][headtrim]xfade=transition=fade:duration=0.15:offset=2.85,"
            + "trim=end=3,setpts=PTS-STARTPTS[out]";
        run(List.of("ffmpeg", "-hide_banner", "-loglevel", "error", "-nostdin", "-y",
            "-threads", "2", "-filter_complex_threads", "1", "-stream_loop", "-1", "-i", source.toString(),
            "-filter_complex", filter, "-map", "[out]", "-an", "-c:v", "libx264", "-threads", "2",
            "-preset", "veryfast", "-crf", "26", "-pix_fmt", "yuv420p", "-movflags", "+faststart",
            "-t", "3", preview.toString()), staging.resolve("preview.log"), 90,
            "视频预览生成失败，请确认文件内容完整且编码格式可由 FFmpeg 解码");
        double previewDuration = probe(preview, staging.resolve("preview-probe.json"));
        if (Math.abs(previewDuration - 3.0) > 0.1)
          throw BusinessException.bad("视频预览时长异常，请确认原文件可正常播放后重新上传");
        run(List.of("ffmpeg", "-hide_banner", "-loglevel", "error", "-nostdin", "-y",
            "-threads", "2", "-ss", "0.35", "-i", preview.toString(), "-frames:v", "1",
            "-threads", "2", "-q:v", "2", staging.resolve("poster.jpg").toString()),
            staging.resolve("poster.log"), 20, "视频封面生成失败，请检查原文件后重试");
        json.writeValue(staging.resolve("metadata.json").toFile(), Map.of("durationSeconds", duration));
        for (String name : List.of("source-probe.json", "preview-probe.json", "preview.log", "poster.log"))
          Files.deleteIfExists(staging.resolve(name));
        Files.move(staging, destination, StandardCopyOption.ATOMIC_MOVE);
        return duration;
      }
    } catch (IOException ex) {
      log.error("素材 {} 的预览文件处理失败", assetId, ex);
      throw new BusinessException(500, "视频预览文件读写失败，请联系管理员检查素材存储目录");
    } finally {
      if (staging != null) deleteDirectory(staging);
    }
  }

  private double probe(Path source, Path output) throws IOException {
    run(List.of("ffprobe", "-v", "error", "-select_streams", "v:0", "-show_entries",
        "stream=codec_type,width,height,duration:format=duration", "-of", "json", source.toString()),
        output, 20, "无法读取视频，请上传内容完整、格式正确的视频文件");
    var info = json.readTree(output.toFile());
    var streams = info.path("streams");
    if (streams.isEmpty() || streams.get(0).path("width").asInt() < 1
        || streams.get(0).path("height").asInt() < 1)
      throw BusinessException.bad("文件中没有可解码的视频画面，请检查文件类型");
    double duration = streams.get(0).path("duration").asDouble(0);
    if (duration <= 0) duration = info.path("format").path("duration").asDouble(0);
    if (!Double.isFinite(duration) || duration <= 0)
      throw BusinessException.bad("无法读取视频时长，请上传包含完整时长信息的视频文件");
    return duration;
  }

  private void run(List<String> command, Path output, int timeoutSeconds, String errorMessage)
      throws IOException {
    Process process;
    try {
      process = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(output.toFile()).start();
    } catch (IOException ex) {
      log.error("无法启动视频处理工具 {}", command.get(0), ex);
      throw new BusinessException(503, "视频处理工具无法启动，请联系管理员检查 FFmpeg 安装");
    }
    try {
      if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
        process.destroyForcibly();
        process.waitFor(5, TimeUnit.SECONDS);
        throw new BusinessException(504, "视频预览生成超时，请检查视频文件后重试");
      }
    } catch (InterruptedException ex) {
      process.destroyForcibly();
      Thread.currentThread().interrupt();
      throw new BusinessException(503, "视频预览生成已中断，请重试");
    }
    if (process.exitValue() != 0) {
      log.warn("{}: {}", errorMessage, Files.readString(output));
      throw BusinessException.bad(errorMessage);
    }
  }

  void discard(long assetId) {
    deleteDirectory(directory(assetId));
  }

  private void deleteDirectory(Path path) {
    if (!Files.exists(path)) return;
    try (var files = Files.walk(path)) {
      for (Path entry : files.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(entry);
    } catch (IOException ex) {
      log.warn("清理视频预览目录失败：{}", path, ex);
    }
  }
}
