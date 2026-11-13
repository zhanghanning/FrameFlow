package com.frameflow.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.frameflow.common.*;
import com.frameflow.integration.*;
import com.frameflow.mapper.*;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.*;

class PlatformIntegrationServiceTest {
  PlatformIntegrationMapper db;
  AuthMapper users;
  StudioMapper studio;
  PlatformProvider provider;
  PlatformTokenCipher cipher;
  PlatformIntegrationService service;
  MockEnvironment env;

  @BeforeEach void setup() {
    db = mock(PlatformIntegrationMapper.class); users = mock(AuthMapper.class); studio = mock(StudioMapper.class);
    provider = mock(PlatformProvider.class); when(provider.code()).thenReturn("YOUTUBE");
    env = new MockEnvironment().withProperty("PLATFORM_TOKEN_KEY", Base64.getEncoder().encodeToString(new byte[32]))
        .withProperty("YOUTUBE_CLIENT_ID", "test-client").withProperty("YOUTUBE_CLIENT_SECRET", "test-secret");
    createService();
    Actor.set(new Actor(10, "creator", "本人", List.of("CREATOR"), 42L));
  }
  @AfterEach void cleanup() { Actor.clear(); }
  void createService() {
    var settings = new PlatformSettings(env); cipher = new PlatformTokenCipher(settings);
    var transaction = mock(TransactionTemplate.class);
    when(transaction.execute(any())).thenAnswer(invocation -> ((TransactionCallback<?>) invocation.getArgument(0)).doInTransaction(mock(TransactionStatus.class)));
    doAnswer(invocation -> { ((java.util.function.Consumer<TransactionStatus>) invocation.getArgument(0)).accept(mock(TransactionStatus.class)); return null; })
        .when(transaction).executeWithoutResult(any());
    service = new PlatformIntegrationService(db, users, studio, settings, cipher, transaction, List.of(provider));
  }
  Map<String, Object> connection(long creatorId) {
    var connection = new HashMap<String, Object>();
    connection.put("id", 12L); connection.put("accountId", 77L); connection.put("creatorId", creatorId);
    connection.put("platformCode", "YOUTUBE"); connection.put("externalId", "CaseSensitive-ID"); connection.put("status", "CONNECTED");
    connection.put("accessToken", cipher.encrypt("access-secret")); connection.put("refreshToken", cipher.encrypt("refresh-secret"));
    connection.put("tokenExpiresAt", LocalDateTime.now().plusHours(1)); connection.put("refreshAvailable", true);
    connection.put("lastSyncedAt", LocalDateTime.now());
    return connection;
  }
  void callbackReady() {
    when(db.platform("YOUTUBE")).thenReturn(Map.of("id", 5L));
    when(db.attempt("state")).thenReturn(Map.of("platformId", 5L, "userId", 10L, "creatorId", 42L, "codeVerifier", "verifier"));
    when(db.consumeAttempt("state", 5L)).thenReturn(1);
    when(users.userById(10)).thenReturn(Map.of("id", 10L, "username", "creator", "displayName", "本人", "creatorId", 42L, "status", "ACTIVE"));
    when(users.roles(10)).thenReturn(List.of("CREATOR")); when(studio.creator(42)).thenReturn(Map.of("status", "ACTIVE"));
  }

  @Test void missingConfigurationBlocksAuthorizationBeforeStateIsCreated() {
    env.setProperty("PLATFORM_TOKEN_KEY", ""); createService();
    when(db.platforms()).thenReturn(List.of(Map.of("code", "YOUTUBE", "name", "YouTube"), Map.of("code", "DOUYIN", "name", "抖音")));
    var catalog = service.integrations();
    assertEquals("NOT_CONFIGURED", catalog.get(0).get("status")); assertEquals(false, catalog.get(0).get("canConnect"));
    assertEquals("APPROVAL_REQUIRED", catalog.get(1).get("status"));
    when(studio.creator(42)).thenReturn(Map.of("status", "ACTIVE"));
    assertEquals(503, assertThrows(BusinessException.class, () -> service.authorize("YOUTUBE", null)).status());
    verify(db, never()).insertAttempt(any());
  }
  @Test void callbackConsumesStateOnceBeforeExchangingCode() {
    when(db.platform("YOUTUBE")).thenReturn(Map.of("id", 5L));
    when(db.attempt("used-state")).thenReturn(Map.of("platformId", 5L));
    when(db.consumeAttempt("used-state", 5L)).thenReturn(0);
    assertEquals(400, assertThrows(BusinessException.class, () -> service.callback("YOUTUBE", "used-state", "code", null)).status());
    verify(provider, never()).exchange(anyString(), anyString());
  }
  @Test void cancelledAuthorizationConsumesStateWithoutExchangingCode() {
    when(db.platform("YOUTUBE")).thenReturn(Map.of("id", 5L));
    when(db.attempt("cancelled")).thenReturn(Map.of("platformId", 5L));
    when(db.consumeAttempt("cancelled", 5L)).thenReturn(1, 0);
    var cancelled = assertThrows(BusinessException.class, () -> service.callback("YOUTUBE", "cancelled", null, "access_denied"));
    assertTrue(cancelled.getMessage().contains("取消"));
    assertEquals(400, assertThrows(BusinessException.class, () -> service.callback("YOUTUBE", "cancelled", "later-code", null)).status());
    verify(db, times(2)).consumeAttempt("cancelled", 5L); verify(provider, never()).exchange(anyString(), anyString());
  }
  @Test void callbackRechecksCurrentPermissions() {
    callbackReady(); when(users.roles(10)).thenReturn(List.of("FINANCE"));
    assertEquals(403, assertThrows(BusinessException.class, () -> service.callback("YOUTUBE", "state", "code", null)).status());
    verify(db).consumeAttempt("state", 5L); verify(provider, never()).exchange(anyString(), anyString());
  }
  @Test void callbackCannotClaimAnotherCreatorsPlatformAccount() {
    callbackReady();
    var token = new PlatformProvider.Token("access", "refresh", LocalDateTime.now().plusHours(1), null, "readonly");
    when(provider.exchange("code", "verifier")).thenReturn(token);
    when(provider.profile(token)).thenReturn(new PlatformProvider.Profile("remote-account", "频道"));
    when(db.accountByExternal(5, "remote-account")).thenReturn(Map.of("id", 77L, "creatorId", 99L));
    assertEquals(409, assertThrows(BusinessException.class, () -> service.callback("YOUTUBE", "state", "code", null)).status());
    verify(db, never()).saveConnection(any()); verify(db, never()).updateAccountName(anyLong(), anyString());
  }
  @Test void synchronizeTraversesPagesAndCountsUniqueRemoteIds() {
    when(db.connection(12)).thenReturn(connection(42));
    doAnswer(invocation -> { ((Map<String,Object>) invocation.getArgument(0)).put("id", 31L); return null; }).when(db).insertJob(any());
    var first = new PlatformProvider.Content("A", "first", null, null, "https://example.test/A", "VIDEO", null, 1L, null, null);
    var updated = new PlatformProvider.Content("A", "updated", null, null, "https://example.test/A", "VIDEO", null, 2L, null, null);
    var other = new PlatformProvider.Content("a", "case differs", null, null, "https://example.test/a", "VIDEO", null, 3L, null, null);
    when(provider.content(any(), eq("CaseSensitive-ID"), isNull())).thenReturn(new PlatformProvider.Page(List.of(first, first), "next"));
    when(provider.content(any(), eq("CaseSensitive-ID"), eq("next"))).thenReturn(new PlatformProvider.Page(List.of(updated, other), null));
    Map<String,Map<String,Object>> persisted = new LinkedHashMap<>();
    doAnswer(invocation -> { Map<String,Object> row = invocation.getArgument(0); persisted.put((String)row.get("externalId"), row); return null; }).when(db).upsertContent(any());
    var result = service.sync(12);
    assertEquals(2, result.get("syncedCount")); assertEquals(Set.of("A", "a"), persisted.keySet());
    assertEquals("updated", persisted.get("A").get("title"));
    verify(db, times(3)).upsertContent(any()); verify(db).synced(12);
  }
  @Test void anotherCreatorCannotSynchronizeOrDisconnect() {
    when(db.connection(12)).thenReturn(connection(99));
    assertEquals(403, assertThrows(BusinessException.class, () -> service.sync(12)).status());
    assertEquals(403, assertThrows(BusinessException.class, () -> service.disconnect(12)).status());
    verify(db, never()).insertJob(any()); verify(db, never()).disconnect(anyLong());
  }
  @Test void staffWithoutCreatorGetsEmptyScopeAndCannotAuthorize() {
    Actor.set(new Actor(20, "finance", "财务", List.of("FINANCE"), null));
    when(db.connections(any())).thenReturn(List.of()); when(db.content(any())).thenReturn(List.of());
    assertEquals(List.of(), service.connections()); assertEquals(List.of(), service.content(Map.of()).get("items"));
    verify(db).connections(argThat(query -> Long.valueOf(-1).equals(query.get("creatorId"))));
    assertEquals(403, assertThrows(BusinessException.class, () -> service.authorize("YOUTUBE", 42L)).status());
  }
  @Test void platformTokensAreEncryptedAndInvalidKeyBlocksDecryption() {
    String saved = cipher.encrypt("private-oauth-token");
    assertFalse(saved.contains("private-oauth-token")); assertEquals("private-oauth-token", cipher.decrypt(saved));
    env.setProperty("PLATFORM_TOKEN_KEY", Base64.getEncoder().encodeToString("12345678901234567890123456789012".getBytes()));
    var otherKey = new PlatformTokenCipher(new PlatformSettings(env));
    assertEquals(503, assertThrows(BusinessException.class, () -> otherKey.decrypt(saved)).status());
  }
}
