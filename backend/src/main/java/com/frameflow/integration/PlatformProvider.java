package com.frameflow.integration;

import java.time.LocalDateTime;
import java.util.List;

public interface PlatformProvider {
  record Token(String accessToken, String refreshToken, LocalDateTime expiresAt,
      LocalDateTime refreshExpiresAt, String scopes) {}
  record Profile(String externalId, String name) {}
  record Content(String externalId, String title, String description, String coverUrl,
      String permalink, String mediaType, LocalDateTime publishedAt,
      Long viewCount, Long likeCount, Long commentCount) {}
  record Page(List<Content> items, String nextCursor) {}
  String code();
  String authorizationUrl(String state, String verifier);
  Token exchange(String code, String verifier);
  Token refresh(Token token);
  boolean needsRefresh(Token token);
  Profile profile(Token token);
  Page content(Token token, String accountExternalId, String cursor);
}
