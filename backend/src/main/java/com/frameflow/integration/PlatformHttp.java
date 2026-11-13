package com.frameflow.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class PlatformHttp {
  private final ObjectMapper json;
  private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
  public PlatformHttp(ObjectMapper json) { this.json = json; }
  public static String form(Map<String, String> fields) {
    return fields.entrySet().stream().map(e -> encode(e.getKey()) + "=" + encode(e.getValue())).collect(Collectors.joining("&"));
  }
  public static String query(String url, Map<String, String> fields) { return url + "?" + form(fields); }
  private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
  public JsonNode get(String url, Map<String, String> fields, String token) {
    var request = HttpRequest.newBuilder(URI.create(query(url, fields))).timeout(Duration.ofSeconds(30)).GET();
    if (token != null) request.header("Authorization", "Bearer " + token);
    return send(request.build());
  }
  public JsonNode post(String url, Map<String, String> fields) {
    return send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30))
        .header("Content-Type", "application/x-www-form-urlencoded")
        .POST(HttpRequest.BodyPublishers.ofString(form(fields))).build());
  }
  private JsonNode send(HttpRequest request) {
    try {
      var response = client.send(request, HttpResponse.BodyHandlers.ofString());
      JsonNode body = json.readTree(response.body());
      if (response.statusCode() >= 400 || body.has("error")) {
        boolean expired = response.statusCode() == 401 || "invalid_grant".equals(body.path("error").asText())
            || body.path("error").path("code").asInt() == 190;
        throw new PlatformRemoteException(expired ? "平台授权已失效，请重新关联账号" : "平台接口拒绝请求，请检查应用权限、配额和账号授权后重试", expired);
      }
      return body;
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new PlatformRemoteException("平台请求已中断，请重试", false);
    } catch (IOException | IllegalArgumentException ex) {
      throw new PlatformRemoteException("平台服务暂时无法访问，请检查网络后重试", false);
    }
  }
  static String text(JsonNode node, String field) { return node.path(field).isNull() || node.path(field).isMissingNode() ? null : node.path(field).asText(); }
  static Long count(JsonNode node, String field) { String value = text(node, field); return value == null ? null : Long.valueOf(value); }
  static LocalDateTime date(String value) { return value == null || value.isBlank() ? null : OffsetDateTime.parse(value.replaceFirst("([+-]\\d{2})(\\d{2})$", "$1:$2")).atZoneSameInstant(ZoneId.of("Asia/Shanghai")).toLocalDateTime(); }
}
