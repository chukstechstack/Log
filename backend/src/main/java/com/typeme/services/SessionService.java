package com.typeme.services;

import com.sun.net.httpserver.HttpExchange;
import com.typeme.config.AuthConfig;
import com.typeme.dao.SessionDao;
import com.typeme.models.User;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

public final class SessionService {
  private static final SecureRandom SECURE_RANDOM = new SecureRandom();

  private SessionService() {}

  public static String createForUser(int userId) throws SQLException {
    String token = createRandomToken();
    SessionDao.create(userId, hashToken(token), Instant.now().plus(AuthConfig.sessionTtl()));
    return token;
  }

  public static User getCurrentUser(HttpExchange exchange) throws SQLException {
    String token = readCookie(exchange, AuthConfig.sessionCookieName());
    return token == null ? null : SessionDao.findUser(hashToken(token));
  }

  public static void revokeCurrentSession(HttpExchange exchange) throws SQLException {
    String token = readCookie(exchange, AuthConfig.sessionCookieName());
    if (token != null) {
      SessionDao.delete(hashToken(token));
    }
  }

  public static String buildCookie(String name, String value, int maxAge, String sameSite) {
    StringBuilder sb =
        new StringBuilder(name)
            .append('=')
            .append(value)
            .append("; Path=/; Max-Age=")
            .append(maxAge)
            .append("; HttpOnly; SameSite=")
            .append(sameSite);
    if (AuthConfig.cookieSecure()) {
      sb.append("; Secure");
    }
    return sb.toString();
  }

  private static String readCookie(HttpExchange exchange, String name) {
    List<String> cookieHeaders = exchange.getRequestHeaders().get("Cookie");
    if (cookieHeaders == null) {
      return null;
    }
    for (String cookieHeader : cookieHeaders) {
      for (String part : cookieHeader.split(";")) {
        String[] pair = part.trim().split("=", 2);
        if (pair.length == 2 && name.equals(pair[0])) {
          return pair[1];
        }
      }
    }
    return null;
  }

  private static String createRandomToken() {
    byte[] bytes = new byte[32];
    SECURE_RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private static String hashToken(String token) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
      return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is not available", e);
    }
  }
}
