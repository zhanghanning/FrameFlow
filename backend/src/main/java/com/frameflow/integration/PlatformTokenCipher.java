package com.frameflow.integration;

import com.frameflow.common.BusinessException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class PlatformTokenCipher {
  private final SecretKeySpec key;
  private final SecureRandom random = new SecureRandom();
  public PlatformTokenCipher(PlatformSettings settings) {
    byte[] bytes;
    try { bytes = Base64.getDecoder().decode(settings.tokenKey()); }
    catch (IllegalArgumentException ex) { bytes = new byte[0]; }
    key = bytes.length == 32 ? new SecretKeySpec(bytes, "AES") : null;
  }
  public boolean configured() { return key != null; }
  public String encrypt(String value) {
    if (value == null || value.isBlank()) return null;
    requireKey();
    try {
      byte[] nonce = new byte[12]; random.nextBytes(nonce);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, nonce));
      byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
      return Base64.getEncoder().encodeToString(ByteBuffer.allocate(nonce.length + encrypted.length).put(nonce).put(encrypted).array());
    } catch (GeneralSecurityException ex) { throw new BusinessException(503, "平台令牌加密失败，请联系管理员检查配置"); }
  }
  public String decrypt(String value) {
    if (value == null || value.isBlank()) return null;
    requireKey();
    try {
      ByteBuffer buffer = ByteBuffer.wrap(Base64.getDecoder().decode(value));
      byte[] nonce = new byte[12]; buffer.get(nonce);
      byte[] encrypted = new byte[buffer.remaining()]; buffer.get(encrypted);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, nonce));
      return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
    } catch (GeneralSecurityException | RuntimeException ex) {
      throw new BusinessException(503, "平台令牌无法解密，请恢复原 PLATFORM_TOKEN_KEY 或重新关联账号");
    }
  }
  public void requireKey() {
    if (!configured()) throw new BusinessException(503, "请先配置 PLATFORM_TOKEN_KEY：Base64 编码的 32 字节密钥");
  }
}
