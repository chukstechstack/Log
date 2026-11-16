package com.typeme.services;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public final class PasswordHasher {
  private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
  private static final int ITERATIONS = 310_000;
  private static final int KEY_LENGTH_BITS = 256;
  private static final int SALT_LENGTH_BYTES = 16;
  private static final SecureRandom SECURE_RANDOM = new SecureRandom();

  private PasswordHasher() {}

  public static String hash(char[] password) {
    byte[] salt = new byte[SALT_LENGTH_BYTES];
    SECURE_RANDOM.nextBytes(salt);
    byte[] derived = derive(password, salt, ITERATIONS);
    return "pbkdf2_sha256$"
        + ITERATIONS
        + "$"
        + Base64.getEncoder().encodeToString(salt)
        + "$"
        + Base64.getEncoder().encodeToString(derived);
  }

  public static boolean verify(char[] password, String encoded) {
    if (encoded == null) {
      return false;
    }
    String[] parts = encoded.split("\\$", -1);
    if (parts.length != 4 || !"pbkdf2_sha256".equals(parts[0])) {
      return false;
    }
    try {
      int iterations = Integer.parseInt(parts[1]);
      if (iterations < 1 || iterations > 2_000_000) {
        return false;
      }
      byte[] salt = Base64.getDecoder().decode(parts[2]);
      byte[] expected = Base64.getDecoder().decode(parts[3]);
      if (salt.length < 8 || expected.length != KEY_LENGTH_BITS / 8) {
        return false;
      }
      return MessageDigest.isEqual(expected, derive(password, salt, iterations));
    } catch (IllegalArgumentException e) {
      return false;
    }
  }

  private static byte[] derive(char[] password, byte[] salt, int iterations) {
    PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, KEY_LENGTH_BITS);
    try {
      return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Password hashing is unavailable", e);
    } finally {
      spec.clearPassword();
    }
  }
}
