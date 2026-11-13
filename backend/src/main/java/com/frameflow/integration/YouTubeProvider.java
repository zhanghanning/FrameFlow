package com.frameflow.integration;

import static com.frameflow.integration.PlatformHttp.*;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class YouTubeProvider implements PlatformProvider {
  private static final String API = "https://www.googleapis.com/youtube/v3/";
  private static final String SCOPE = "https://www.googleapis.com/auth/youtube.readonly";
  private final PlatformSettings settings;
  private final PlatformHttp http;
  public YouTubeProvider(PlatformSettings settings, PlatformHttp http) { this.settings = settings; this.http = http; }
  public String code() { return "YOUTUBE"; }
  public String authorizationUrl(String state, String verifier) {
    String challenge;
    try { challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII))); }
    catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    var fields = new LinkedHashMap<String, String>();
    fields.put("client_id", settings.clientId(code())); fields.put("redirect_uri", settings.redirectUri(code()));
    fields.put("response_type", "code"); fields.put("scope", SCOPE); fields.put("state", state);
    fields.put("access_type", "offline"); fields.put("prompt", "consent"); fields.put("include_granted_scopes", "true");
    fields.put("code_challenge", challenge); fields.put("code_challenge_method", "S256");
    return query("https://accounts.google.com/o/oauth2/v2/auth", fields);
  }
  public Token exchange(String authorizationCode, String verifier) {
    var response = http.post("https://oauth2.googleapis.com/token", Map.of(
        "client_id", settings.clientId(code()), "client_secret", settings.clientSecret(code()),
        "code", authorizationCode, "redirect_uri", settings.redirectUri(code()),
        "grant_type", "authorization_code", "code_verifier", verifier));
    return token(response, null);
  }
  public Token refresh(Token old) {
    if (old.refreshToken() == null || (old.refreshExpiresAt() != null && old.refreshExpiresAt().isBefore(LocalDateTime.now())))
      throw new PlatformRemoteException("YouTube 授权已到期，请重新关联账号", true);
    var response = http.post("https://oauth2.googleapis.com/token", Map.of(
        "client_id", settings.clientId(code()), "client_secret", settings.clientSecret(code()),
        "refresh_token", old.refreshToken(), "grant_type", "refresh_token"));
    return token(response, old);
  }
  private Token token(JsonNode response, Token old) {
    String access = text(response, "access_token");
    long expires = response.path("expires_in").asLong();
    if (access == null || expires <= 0) throw new PlatformRemoteException("YouTube 未返回有效授权信息，请检查应用配置后重试", false);
    String refresh = text(response, "refresh_token");
    String scopes = text(response, "scope");
    Long refreshSeconds = count(response, "refresh_token_expires_in");
    return new Token(access, refresh != null ? refresh : old == null ? null : old.refreshToken(),
        LocalDateTime.now().plusSeconds(expires), refreshSeconds != null ? LocalDateTime.now().plusSeconds(refreshSeconds) : old == null ? null : old.refreshExpiresAt(),
        scopes != null ? scopes : old == null ? SCOPE : old.scopes());
  }
  public boolean needsRefresh(Token token) { return token.expiresAt() != null && token.expiresAt().isBefore(LocalDateTime.now().plusMinutes(2)); }
  public Profile profile(Token token) {
    var items = http.get(API + "channels", Map.of("part", "snippet", "mine", "true"), token.accessToken()).path("items");
    if (items.isEmpty()) throw new PlatformRemoteException("授权账号尚未建立 YouTube 频道，请先创建频道", false);
    var channel = items.get(0);
    return new Profile(channel.path("id").asText(), channel.path("snippet").path("title").asText());
  }
  public Page content(Token token, String channelId, String cursor) {
    var channels = http.get(API + "channels", Map.of("part", "contentDetails", "id", channelId), token.accessToken()).path("items");
    if (channels.isEmpty()) throw new PlatformRemoteException("无法读取已关联的 YouTube 频道，请重新授权", true);
    String playlist = channels.get(0).path("contentDetails").path("relatedPlaylists").path("uploads").asText();
    var query = new LinkedHashMap<String, String>();
    query.put("part", "contentDetails"); query.put("playlistId", playlist); query.put("maxResults", "50");
    if (cursor != null) query.put("pageToken", cursor);
    var page = http.get(API + "playlistItems", query, token.accessToken());
    var ids = new ArrayList<String>();
    for (var item : page.path("items")) ids.add(item.path("contentDetails").path("videoId").asText());
    var rows = new ArrayList<Content>();
    if (!ids.isEmpty()) {
      var videos = http.get(API + "videos", Map.of("part", "snippet,statistics,contentDetails,status", "id", String.join(",", ids)), token.accessToken());
      for (var video : videos.path("items")) {
        var snippet = video.path("snippet"); var stats = video.path("statistics");
        String cover = null;
        for (String quality : List.of("maxres", "standard", "high", "medium", "default")) {
          cover = text(snippet.path("thumbnails").path(quality), "url");
          if (cover != null) break;
        }
        String id = video.path("id").asText();
        rows.add(new Content(id, snippet.path("title").asText(), text(snippet, "description"), cover,
            "https://www.youtube.com/watch?v=" + id, "VIDEO", date(text(snippet, "publishedAt")),
            count(stats, "viewCount"), count(stats, "likeCount"), count(stats, "commentCount")));
      }
    }
    return new Page(rows, text(page, "nextPageToken"));
  }
}
