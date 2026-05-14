package com.jianbo.localaiknowledge.crypto;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * 用户 API Key 入库加密（AES-256-GCM）。密文为 Base64( IV(12) || ciphertext+tag )。
 *
 * <p>密钥来源：优先 {@code app.user-chat-model.encryption-secret}（建议至少 32 字符）；
 * 未配置时由 {@code app.jwt.secret} 派生固定 AES 密钥（便于本地开发，生产请显式配置）。
 */
@Component
public class UserApiKeyCrypto {

  private static final int GCM_IV_LEN = 12;
  private static final int GCM_TAG_BITS = 128;
  private static final String KDF_SALT = "|local-ai-user-chat-api-key-v1|";

  @Value("${app.user-chat-model.encryption-secret:}")
  private String explicitSecret;

  @Value("${app.jwt.secret}")
  private String jwtSecret;

  private final SecureRandom random = new SecureRandom();

  private SecretKey aesKey() {
    byte[] raw;
    if (explicitSecret != null && !explicitSecret.isBlank()) {
      raw = sha256(explicitSecret.getBytes(StandardCharsets.UTF_8));
    } else {
      raw = sha256((jwtSecret + KDF_SALT).getBytes(StandardCharsets.UTF_8));
    }
    return new SecretKeySpec(raw, "AES");
  }

  private static byte[] sha256(byte[] input) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(input);
    } catch (Exception e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }

  public String encrypt(String plainText) {
    if (plainText == null || plainText.isEmpty()) {
      throw new IllegalArgumentException("apiKey 不能为空");
    }
    try {
      byte[] iv = new byte[GCM_IV_LEN];
      random.nextBytes(iv);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.ENCRYPT_MODE, aesKey(), new GCMParameterSpec(GCM_TAG_BITS, iv));
      byte[] ct = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
      byte[] out = new byte[iv.length + ct.length];
      System.arraycopy(iv, 0, out, 0, iv.length);
      System.arraycopy(ct, 0, out, iv.length, ct.length);
      return Base64.getEncoder().encodeToString(out);
    } catch (Exception e) {
      throw new IllegalStateException("加密 API Key 失败", e);
    }
  }

  public String decrypt(String cipherTextB64) {
    if (cipherTextB64 == null || cipherTextB64.isBlank()) {
      return "";
    }
    try {
      byte[] all = Base64.getDecoder().decode(cipherTextB64.trim());
      if (all.length < GCM_IV_LEN + 2) {
        throw new IllegalArgumentException("密文格式无效");
      }
      byte[] iv = Arrays.copyOfRange(all, 0, GCM_IV_LEN);
      byte[] ct = Arrays.copyOfRange(all, GCM_IV_LEN, all.length);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.DECRYPT_MODE, aesKey(), new GCMParameterSpec(GCM_TAG_BITS, iv));
      byte[] pt = cipher.doFinal(ct);
      return new String(pt, StandardCharsets.UTF_8);
    } catch (Exception e) {
      throw new IllegalStateException("解密 API Key 失败", e);
    }
  }
}
