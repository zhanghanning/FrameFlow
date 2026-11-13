package com.frameflow.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class PlatformProviderTest {
  @Test void officialTimestampFormatsConvertToShanghaiTime() {
    assertEquals(LocalDateTime.of(2019, 9, 27, 6, 36, 43), PlatformHttp.date("2019-09-26T22:36:43+0000"));
    assertEquals(LocalDateTime.of(2019, 9, 27, 6, 36, 43), PlatformHttp.date("2019-09-26T22:36:43Z"));
  }
  @Test void instagramReadsOfficialProfileEnvelopeAndPagination() throws Exception {
    var http = mock(PlatformHttp.class); var json = new ObjectMapper();
    var provider = new InstagramProvider(new PlatformSettings(new MockEnvironment()), http);
    var token = new PlatformProvider.Token("token", null, LocalDateTime.now().plusDays(60), null, "instagram_business_basic");
    when(http.get(eq("https://graph.instagram.com/v27.0/me"), anyMap(), isNull()))
        .thenReturn(json.readTree("{\"data\":[{\"user_id\":\"IGID\",\"username\":\"owner\"}]}"));
    assertEquals("IGID", provider.profile(token).externalId());
    when(http.get(eq("https://graph.instagram.com/v27.0/IGID/media"), anyMap(), isNull()))
        .thenReturn(json.readTree("{\"data\":[{\"id\":\"media1\",\"media_type\":\"VIDEO\",\"timestamp\":\"2019-09-26T22:36:43+0000\",\"thumbnail_url\":\"https://example.test/cover.jpg\",\"permalink\":\"https://www.instagram.com/p/example/\"}],\"paging\":{\"next\":\"https://graph.instagram.com/next\",\"cursors\":{\"after\":\"NEXT\"}}}"));
    var page = provider.content(token, "IGID", null);
    assertEquals("NEXT", page.nextCursor()); assertEquals("https://example.test/cover.jpg", page.items().getFirst().coverUrl());
    assertNull(page.items().getFirst().viewCount());
  }
  @Test void youtubeRefreshKeepsRefreshTokenAndUsesReturnedLifetime() throws Exception {
    var http = mock(PlatformHttp.class); var json = new ObjectMapper();
    var settings = new PlatformSettings(new MockEnvironment().withProperty("YOUTUBE_CLIENT_ID", "client").withProperty("YOUTUBE_CLIENT_SECRET", "secret"));
    var provider = new YouTubeProvider(settings, http);
    var old = new PlatformProvider.Token("old-access", "saved-refresh", LocalDateTime.now().minusMinutes(1), null, "readonly");
    assertTrue(provider.needsRefresh(old));
    when(http.post(eq("https://oauth2.googleapis.com/token"), anyMap()))
        .thenReturn(json.readTree("{\"access_token\":\"fresh-access\",\"expires_in\":1800,\"scope\":\"readonly\"}"));
    var before = LocalDateTime.now(); var refreshed = provider.refresh(old);
    assertEquals("fresh-access", refreshed.accessToken()); assertEquals("saved-refresh", refreshed.refreshToken());
    assertFalse(refreshed.expiresAt().isBefore(before.plusSeconds(1800)));
    assertTrue(refreshed.expiresAt().isBefore(LocalDateTime.now().plusSeconds(1801)));
    verify(http).post(eq("https://oauth2.googleapis.com/token"), argThat(fields -> "refresh_token".equals(fields.get("grant_type")) && "saved-refresh".equals(fields.get("refresh_token"))));
  }
  @Test void instagramRequiresHttpsAndYoutubeAllowsLocalDevelopmentCallback() {
    var settings = new PlatformSettings(new MockEnvironment());
    assertTrue(settings.redirectValid("YOUTUBE")); assertFalse(settings.redirectValid("INSTAGRAM"));
  }
}
