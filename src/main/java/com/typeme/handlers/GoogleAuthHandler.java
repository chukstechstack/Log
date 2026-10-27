package com.typeme.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.typeme.dao.UserDao;
import com.typeme.models.User;
import com.typeme.services.SessionService;
import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GoogleAuthHandler implements HttpHandler {
  private static final class AuthConfig {
    private static String value(String key, String defaultValue) {
      String configured = System.getProperty(key);
      if (configured == null || configured.isBlank()) {
        configured = System.getenv(key);
      }
      return configured == null || configured.isBlank() ? defaultValue : configured;
    }

    private static String frontendOrigin() {
      return value("FRONTEND_ORIGIN", "http://localhost:3000");
    }

    private static String googleClientId() {
      return value("GOOGLE_CLIENT_ID", "");
    }

    private static String googleClientSecret() {
      return value("GOOGLE_CLIENT_SECRET", "");
    }

    private static String googleRedirectUri() {
      return value("GOOGLE_REDIRECT_URI", "http://localhost:8080/auth/google/callback");
    }

    private static String stateCookieName() {
      return value("AUTH_STATE_COOKIE_NAME", "google_oauth_state");
    }

    private static String sessionCookieName() {
      return value("AUTH_SESSION_COOKIE_NAME", "typeme_session");
    }

    private static String cookieSameSite() {
      return value("AUTH_COOKIE_SAME_SITE", "Lax");
    }

    private static Duration sessionTtl() {
      return Duration.ofSeconds(Long.parseLong(value("AUTH_SESSION_TTL_SECONDS", "86400")));
    }

    private static boolean cookieSecure() {
      return Boolean.parseBoolean(value("AUTH_COOKIE_SECURE", "false"));
    }
  }

  private static final String REDIRECT_PATH = "/dashboard?logged_in=true";
  private static final int STATE_TTL_SECONDS = 300;
  private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
  private static final SecureRandom SECURE_RANDOM = new SecureRandom();

  private final HttpClient httpClient = HttpClient.newHttpClient();

  @Override
  public void handle(HttpExchange exchange) throws IOException {
    applyCorsHeaders(exchange);
    exchange.getResponseHeaders().set("Cache-Control", "no-store");

    String method = exchange.getRequestMethod();
    String path = exchange.getRequestURI().getPath();
    if ("OPTIONS".equalsIgnoreCase(method)) {
      handlePreflight(exchange);
      return;
    }

    try {
      if ("/auth/google".equals(path) && "GET".equalsIgnoreCase(method)) {
        handleLoginRedirect(exchange);
      } else if ("/auth/google/callback".equals(path) && "GET".equalsIgnoreCase(method)) {
        handleCallback(exchange);
      } else if ("/auth/session".equals(path) && "GET".equalsIgnoreCase(method)) {
        handleSession(exchange);
      } else if ("/auth/logout".equals(path) && "POST".equalsIgnoreCase(method)) {
        handleLogout(exchange);
      } else if ("/auth/logout".equals(path)) {
        exchange.getResponseHeaders().set("Allow", "POST, OPTIONS");
        sendError(exchange, 405, "method_not_allowed");
      } else {
        sendError(exchange, 404, "not_found");
      }
    } catch (SQLException e) {
      System.err.println("[Auth] Database operation failed: " + e.getMessage());
      sendError(exchange, 500, "authentication_service_unavailable");
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      sendError(exchange, 500, "authentication_request_interrupted");
    } catch (Exception e) {
      System.err.println("[Auth] Request failed: " + e.getMessage());
      sendError(exchange, 500, "authentication_failed");
    } finally {
      exchange.close();
    }
  }

  private void applyCorsHeaders(HttpExchange exchange) {
    Headers headers = exchange.getResponseHeaders();
    headers.add("Vary", "Origin");
    String origin = exchange.getRequestHeaders().getFirst("Origin");
    if (AuthConfig.frontendOrigin().equals(origin)) {
      headers.set("Access-Control-Allow-Origin", origin);
      headers.set("Access-Control-Allow-Credentials", "true");
      headers.set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
      headers.set("Access-Control-Allow-Headers", "Content-Type");
      headers.set("Access-Control-Max-Age", "600");
    }
  }

  private void handlePreflight(HttpExchange exchange) throws IOException {
    String origin = exchange.getRequestHeaders().getFirst("Origin");
    if (!AuthConfig.frontendOrigin().equals(origin)) {
      sendError(exchange, 403, "origin_not_allowed");
      return;
    }
    exchange.sendResponseHeaders(204, -1);
  }

  private void handleLoginRedirect(HttpExchange exchange) throws Exception {
    String clientId = AuthConfig.googleClientId();
    if (clientId == null || clientId.isBlank()
        || AuthConfig.googleClientSecret() == null
        || AuthConfig.googleClientSecret().isBlank()) {
      throw new IOException("GOOGLE_CLIENT_ID and GOOGLE_CLIENT_SECRET must be configured");
    }

    String state = createStateToken();
    exchange
        .getResponseHeaders()
        .add(
            "Set-Cookie",
            cookie(
                AuthConfig.stateCookieName(),
                state,
                STATE_TTL_SECONDS,
                AuthConfig.cookieSameSite()));

    String googleAuthUrl =
        "https://accounts.google.com/o/oauth2/v2/auth?client_id="
            + encodeFormValue(clientId)
            + "&redirect_uri="
            + encodeFormValue(AuthConfig.googleRedirectUri())
            + "&response_type=code"
            + "&scope="
            + encodeFormValue("openid email profile")
            + "&state="
            + encodeFormValue(state);
    exchange.getResponseHeaders().set("Location", googleAuthUrl);
    exchange.sendResponseHeaders(302, -1);
  }

  private void handleCallback(HttpExchange exchange) throws Exception {
    Map<String, String> params = parseQuery(exchange.getRequestURI().getRawQuery());
    String stateFromCookie = readCookie(exchange, AuthConfig.stateCookieName());
    String stateFromQuery = params.get("state");
    exchange
        .getResponseHeaders()
        .add("Set-Cookie", cookie(AuthConfig.stateCookieName(), "", 0, AuthConfig.cookieSameSite()));

    if (stateFromCookie == null
        || stateFromQuery == null
        || !MessageDigest.isEqual(
            stateFromCookie.getBytes(StandardCharsets.UTF_8),
            stateFromQuery.getBytes(StandardCharsets.UTF_8))) {
      sendError(exchange, 400, "oauth_state_invalid_or_expired");
      return;
    }

    String googleError = params.get("error");
    if (googleError != null) {
      sendError(exchange, 400, "google_authorization_failed: " + googleError);
      return;
    }

    String code = params.get("code");
    if (code == null || code.isBlank()) {
      sendError(exchange, 400, "authorization_code_missing");
      return;
    }

    String accessToken = exchangeCodeForAccessToken(code);
    Map<String, String> googleUser = fetchGoogleUserInfo(accessToken);

    String googleId = googleUser.get("sub");
    String email = googleUser.get("email");
    if (googleId == null || googleId.isBlank() || email == null || email.isBlank()) {
      throw new IOException("Google user profile did not include the required subject or email");
    }

    User user =
        UserDao.findOrCreateByGoogleId(
            googleId, email, googleUser.get("name"), googleUser.get("picture"));
    String sessionToken = SessionService.createForUser(user.getId());

    exchange
        .getResponseHeaders()
        .add(
            "Set-Cookie",
            cookie(
                AuthConfig.sessionCookieName(),
                sessionToken,
                Math.toIntExact(AuthConfig.sessionTtl().getSeconds()),
                AuthConfig.cookieSameSite()));
    exchange
        .getResponseHeaders()
        .set("Location", AuthConfig.frontendOrigin() + REDIRECT_PATH);
    exchange.sendResponseHeaders(302, -1);
  }

  private void handleSession(HttpExchange exchange) throws SQLException, IOException {
    User user = SessionService.getCurrentUser(exchange);
    if (user == null) {
      exchange
          .getResponseHeaders()
          .add(
              "Set-Cookie",
              cookie(AuthConfig.sessionCookieName(), "", 0, AuthConfig.cookieSameSite()));
      sendError(exchange, 401, "unauthenticated");
      return;
    }

    ObjectNode result = JSON_MAPPER.createObjectNode();
    result.put("id", user.getId());
    result.put("email", user.getEmail());
    putNullable(result, "name", user.getName());
    putNullable(result, "avatarUrl", user.getAvatarUrl());
    result.put("admin", user.isAdmin());
    sendJson(exchange, 200, result);
  }

  private void handleLogout(HttpExchange exchange) throws SQLException, IOException {
    String origin = exchange.getRequestHeaders().getFirst("Origin");
    if (!AuthConfig.frontendOrigin().equals(origin)) {
      sendError(exchange, 403, "origin_not_allowed");
      return;
    }

    SessionService.revokeCurrentSession(exchange);
    exchange
        .getResponseHeaders()
        .add(
            "Set-Cookie",
            cookie(AuthConfig.sessionCookieName(), "", 0, AuthConfig.cookieSameSite()));
    exchange.sendResponseHeaders(204, -1);
  }

  private String exchangeCodeForAccessToken(String code)
      throws IOException, InterruptedException {
    String payload =
        "code="
            + encodeFormValue(code)
            + "&client_id="
            + encodeFormValue(AuthConfig.googleClientId())
            + "&client_secret="
            + encodeFormValue(AuthConfig.googleClientSecret())
            + "&redirect_uri="
            + encodeFormValue(AuthConfig.googleRedirectUri())
            + "&grant_type=authorization_code";

    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(URI.create("https://oauth2.googleapis.com/token"))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(payload))
            .build();

    HttpResponse<String> response =
        httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    JsonNode body = readGoogleJson(response.body(), "token exchange");
    if (response.statusCode() < 200 || response.statusCode() >= 300) {
      throw googleApiError("token exchange", response.statusCode(), body);
    }

    String accessToken = jsonText(body, "access_token");
    if (accessToken == null || accessToken.isBlank()) {
      throw new IOException("Google token response did not contain an access token");
    }
    return accessToken;
  }

  private Map<String, String> fetchGoogleUserInfo(String accessToken)
      throws IOException, InterruptedException {
    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(URI.create("https://www.googleapis.com/oauth2/v3/userinfo"))
            .header("Authorization", "Bearer " + accessToken)
            .GET()
            .build();

    HttpResponse<String> response =
        httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    JsonNode body = readGoogleJson(response.body(), "user profile request");
    if (response.statusCode() < 200 || response.statusCode() >= 300) {
      throw googleApiError("user profile request", response.statusCode(), body);
    }

    Map<String, String> userMap = new HashMap<>();
    userMap.put("sub", jsonText(body, "sub"));
    userMap.put("email", jsonText(body, "email"));
    userMap.put("name", jsonText(body, "name"));
    userMap.put("picture", jsonText(body, "picture"));
    return userMap;
  }

  private JsonNode readGoogleJson(String json, String operation) throws IOException {
    JsonNode body = JSON_MAPPER.readTree(json);
    if (body == null || !body.isObject()) {
      throw new IOException("Google " + operation + " returned an invalid response");
    }
    return body;
  }

  private IOException googleApiError(String operation, int statusCode, JsonNode body) {
    String details = jsonText(body, "error_description");
    if (details == null || details.isBlank()) {
      details = jsonText(body, "error");
    }
    String message = "Google " + operation + " failed (HTTP " + statusCode + ")";
    return new IOException(
        details == null || details.isBlank() ? message : message + ": " + details);
  }

  private String jsonText(JsonNode body, String field) {
    JsonNode value = body.get(field);
    return value == null || value.isNull() ? null : value.asText();
  }

  private void putNullable(ObjectNode target, String field, String value) {
    if (value == null) {
      target.putNull(field);
    } else {
      target.put(field, value);
    }
  }

  private void sendJson(HttpExchange exchange, int status, JsonNode body) throws IOException {
    byte[] bytes = JSON_MAPPER.writeValueAsBytes(body);
    exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
    exchange.sendResponseHeaders(status, bytes.length);
    exchange.getResponseBody().write(bytes);
  }

  private void sendError(HttpExchange exchange, int status, String message) throws IOException {
    ObjectNode error = JSON_MAPPER.createObjectNode();
    error.put("error", message);
    sendJson(exchange, status, error);
  }

  private String cookie(String name, String value, int maxAge, String sameSite) {
    StringBuilder result =
        new StringBuilder(name)
            .append('=')
            .append(value)
            .append("; Path=/; Max-Age=")
            .append(maxAge)
            .append("; HttpOnly; SameSite=")
            .append(sameSite);
    if (AuthConfig.cookieSecure()) {
      result.append("; Secure");
    }
    return result.toString();
  }

  private Map<String, String> parseQuery(String query) {
    Map<String, String> map = new HashMap<>();
    if (query != null) {
      for (String param : query.split("&")) {
        String[] pair = param.split("=", 2);
        String key = URLDecoder.decode(pair[0], StandardCharsets.UTF_8);
        String value =
            pair.length > 1 ? URLDecoder.decode(pair[1], StandardCharsets.UTF_8) : "";
        map.put(key, value);
      }
    }
    return map;
  }

  private String encodeFormValue(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  private String readCookie(HttpExchange exchange, String name) {
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

  private String createStateToken() {
    byte[] bytes = new byte[32];
    SECURE_RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }
}
