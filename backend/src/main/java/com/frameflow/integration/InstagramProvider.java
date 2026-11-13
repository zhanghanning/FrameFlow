package com.frameflow.integration;

import static com.frameflow.integration.PlatformHttp.*;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class InstagramProvider implements PlatformProvider {
  private static final String API = "https://graph.instagram.com/v27.0/";
  private static final String SCOPE = "instagram_business_basic";
  private final PlatformSettings settings;
  private final PlatformHttp http;
  public InstagramProvider(PlatformSettings settings, PlatformHttp http) { this.settings = settings; this.http = http; }
  public String code() { return "INSTAGRAM"; }
  public String authorizationUrl(String state, String verifier) {
    return query("https://www.instagram.com/oauth/authorize", Map.of("client_id", settings.clientId(code()),
        "redirect_uri", settings.redirectUri(code()), "response_type", "code", "scope", SCOPE, "state", state));
  }
  public Token exchange(String authorizationCode, String verifier) {
    var response = http.post("https://api.instagram.com/oauth/access_token", Map.of(
        "client_id", settings.clientId(code()), "client_secret", settings.clientSecret(code()),
        "grant_type", "authorization_code", "redirect_uri", settings.redirectUri(code()), "code", authorizationCode));
    var granted = response.path("data").isArray() && !response.path("data").isEmpty() ? response.path("data").get(0) : response;
    String shortToken = text(granted, "access_token");
    if (shortToken == null) throw new PlatformRemoteException("Instagram 未返回有效授权，请检查专业账号与应用配置", false);
    var permissions = granted.path("permissions");
    var scopes = new ArrayList<String>();
    if (permissions.isArray()) permissions.forEach(permission -> scopes.add(permission.asText()));
    else if (permissions.isTextual()) scopes.add(permissions.asText());
    return token(http.get("https://graph.instagram.com/access_token", Map.of("grant_type", "ig_exchange_token",
        "client_secret", settings.clientSecret(code()), "access_token", shortToken), null), scopes.isEmpty() ? SCOPE : String.join(",", scopes));
  }
  public Token refresh(Token old) {
    if (old.expiresAt() == null || !old.expiresAt().isAfter(LocalDateTime.now()))
      throw new PlatformRemoteException("Instagram 授权已过期，请重新关联账号", true);
    return token(http.get("https://graph.instagram.com/refresh_access_token", Map.of(
        "grant_type", "ig_refresh_token", "access_token", old.accessToken()), null), old.scopes());
  }
  private Token token(JsonNode response, String scopes) {
    String access = text(response, "access_token");
    long expires = response.path("expires_in").asLong();
    if (access == null || expires <= 0) throw new PlatformRemoteException("Instagram 未返回长期授权信息，请重新关联账号", false);
    return new Token(access, null, LocalDateTime.now().plusSeconds(expires), null, scopes);
  }
  // 长期令牌约60天；到期前7天刷新，此时已满足签发24小时后的刷新条件。
  public boolean needsRefresh(Token token) { return token.expiresAt() != null && token.expiresAt().isBefore(LocalDateTime.now().plusDays(7)); }
  public Profile profile(Token token) {
    var profile = http.get(API + "me", Map.of("fields", "user_id,username,name,account_type", "access_token", token.accessToken()), null);
    if (profile.path("data").isArray() && !profile.path("data").isEmpty()) profile = profile.path("data").get(0);
    String id = text(profile, "user_id");
    if (id == null) throw new PlatformRemoteException("未取得 Instagram 专业账号身份，请核对账号类型和授权范围", false);
    return new Profile(id, profile.path("username").asText());
  }
  public Page content(Token token, String accountId, String cursor) {
    var query = new LinkedHashMap<String, String>();
    query.put("fields", "id,caption,media_type,media_product_type,media_url,permalink,thumbnail_url,timestamp,like_count,comments_count");
    query.put("access_token", token.accessToken()); query.put("limit", "50");
    if (cursor != null) query.put("after", cursor);
    var response = http.get(API + accountId + "/media", query, null);
    var rows = new ArrayList<Content>();
    for (var media : response.path("data")) {
      String caption = text(media, "caption"), type = media.path("media_type").asText();
      String title = caption == null || caption.isBlank() ? "Instagram 作品 " + media.path("id").asText() : caption.lines().findFirst().orElse(caption);
      if (title.length() > 500) title = title.substring(0, 500);
      rows.add(new Content(media.path("id").asText(), title, caption,
          text(media, "VIDEO".equals(type) ? "thumbnail_url" : "media_url"), text(media, "permalink"), type,
          date(text(media, "timestamp")), null, count(media, "like_count"), count(media, "comments_count")));
    }
    String next = response.path("paging").hasNonNull("next") ? text(response.path("paging").path("cursors"), "after") : null;
    return new Page(rows, next);
  }
}
