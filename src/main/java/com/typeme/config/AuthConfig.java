package com.typeme.config;

import java.net.URI;
import java.time.Duration;
import java.util.Locale;

public final class AuthConfig {
  private static final String DEFAULT_FRONTEND_ORIGIN = "http://localhost:4200";
  private static final Duration SESSION_TTL = Duration.ofHours(12);

  private static final String GOOGLE_CLIENT_ID = System.getenv("GOOGLE_CLIENT_ID");
  private static final String GOOGLE_CLIENT_SECRET = System.getenv("GOOGLE_CLIENT_SECRET");
  private static final String GOOGLE_REDIRECT_URI =
      environmentOrDefault(
          "GOOGLE_REDIRECT_URI", "http://localhost:7070/auth/google/callback");
  private static final String FRONTEND_ORIGIN =
      normalizeOrigin(environmentOrDefault("FRONTEND_ORIGIN", DEFAULT_FRONTEND_ORIGIN));
  private static final boolean COOKIE_SECURE =
      Boolean.parseBoolean(
          environmentOrDefault(
              "SESSION_COOKIE_SECURE", GOOGLE_REDIRECT_URI.startsWith("https://") ? "true" : "false"));
  private static final String COOKIE_SAME_SITE =
      environmentOrDefault("SESSION_COOKIE_SAME_SITE", "Lax");
  private static final String COOKIE_NAME = COOKIE_SECURE ? "__Host-session_id" : "session_id";
  private static final String STATE_COOKIE_NAME =
      COOKIE_SECURE ? "__Host-oauth_state" : "oauth_state";

  static {
    if (!COOKIE_SAME_SITE.equalsIgnoreCase("Lax")
        && !COOKIE_SAME_SITE.equalsIgnoreCase("Strict")
        && !COOKIE_SAME_SITE.equalsIgnoreCase("None")) {
      throw new IllegalStateException(
          "SESSION_COOKIE_SAME_SITE must be Lax, Strict, or None");
    }
    if (COOKIE_SAME_SITE.equalsIgnoreCase("None") && !COOKIE_SECURE) {
      throw new IllegalStateException(
          "SESSION_COOKIE_SECURE must be true when SESSION_COOKIE_SAME_SITE is None");
    }
  }

  private AuthConfig() {}

  public static String googleClientId() {
    return GOOGLE_CLIENT_ID;
  }

  public static String googleClientSecret() {
    return GOOGLE_CLIENT_SECRET;
  }

  public static String googleRedirectUri() {
    return GOOGLE_REDIRECT_URI;
  }

  public static String frontendOrigin() {
    return FRONTEND_ORIGIN;
  }

  public static boolean cookieSecure() {
    return COOKIE_SECURE;
  }

  public static String cookieSameSite() {
    return COOKIE_SAME_SITE.toLowerCase(Locale.ROOT);
  }

  public static String sessionCookieName() {
    return COOKIE_NAME;
  }

  public static String stateCookieName() {
    return STATE_COOKIE_NAME;
  }

  public static Duration sessionTtl() {
    return SESSION_TTL;
  }

  private static String environmentOrDefault(String name, String defaultValue) {
    String value = System.getenv(name);
    return value == null || value.isBlank() ? defaultValue : value.trim();
  }

  private static String normalizeOrigin(String value) {
    URI uri;
    try {
      uri = URI.create(value);
    } catch (IllegalArgumentException e) {
      throw new IllegalStateException("FRONTEND_ORIGIN must be a valid HTTP(S) origin", e);
    }
    if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
        || uri.getHost() == null
        || uri.getUserInfo() != null
        || (uri.getPath() != null && !uri.getPath().isEmpty() && !"/".equals(uri.getPath()))
        || uri.getQuery() != null
        || uri.getFragment() != null) {
      throw new IllegalStateException(
          "FRONTEND_ORIGIN must contain only an HTTP(S) scheme, host, and optional port");
    }
    return uri.getScheme() + "://" + uri.getRawAuthority();
  }
}
