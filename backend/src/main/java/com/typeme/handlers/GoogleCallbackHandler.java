package com.typeme.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
//
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
//
import com.typeme.config.AuthConfig;
import com.typeme.dao.UserDao;
import com.typeme.models.User;
import com.typeme.services.SessionService;
//
import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class GoogleCallbackHandler implements HttpHandler {
  private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
  private final HttpClient httpClient = HttpClient.newHttpClient();

  @Override
  public void handle(HttpExchange exchange) throws IOException {
    if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
      exchange.getResponseHeaders().set("Allow", "GET");
      exchange.sendResponseHeaders(405, -1);
      return;
    }

    try {
      Map<String, String> params = parseQuery(exchange.getRequestURI().getRawQuery());

      // Clear state cookie immediately
      exchange
          .getResponseHeaders()
          .add(
              "Set-Cookie",
              SessionService.buildCookie(
                  AuthConfig.stateCookieName(), "", 0, AuthConfig.cookieSameSite()));

      String code = params.get("code");
      if (code == null || code.isBlank()) {
        exchange.sendResponseHeaders(400, -1);
        return;
      }

      String accessToken = exchangeCodeForAccessToken(code);

      Map<String, String> googleUser = fetchGoogleUserInfo(accessToken);

      String googleId = googleUser.get("sub");
      String email = googleUser.get("email");
      if (googleId == null || email == null) {
        exchange.sendResponseHeaders(500, -1);
        return;
      }

      User user =
          UserDao.findOrCreateByGoogleId(
              googleId, email, googleUser.get("name"), googleUser.get("picture"));

      String sessionToken = SessionService.createForUser(user.getId());

      int maxAge = Math.toIntExact(AuthConfig.sessionTtl().getSeconds());
      exchange
          .getResponseHeaders()
          .add(
              "Set-Cookie",
              SessionService.buildCookie(
                  AuthConfig.sessionCookieName(),
                  sessionToken,
                  maxAge,
                  AuthConfig.cookieSameSite()));

      exchange
          .getResponseHeaders()
          .set("Location", AuthConfig.frontendOrigin() + "/dashboard?logged_in=true");
      exchange.sendResponseHeaders(302, -1);
    } catch (Exception e) {
      System.err.println("[Auth Callback] Error: " + e.getMessage());
      exchange.sendResponseHeaders(500, -1);
    }
  }

  private String exchangeCodeForAccessToken(String code) throws IOException, InterruptedException {
    String payload =
        "code="
            + URLEncoder.encode(code, StandardCharsets.UTF_8)
            + "&client_id="
            + URLEncoder.encode(AuthConfig.googleClientId(), StandardCharsets.UTF_8)
            + "&client_secret="
            + URLEncoder.encode(AuthConfig.googleClientSecret(), StandardCharsets.UTF_8)
            + "&redirect_uri="
            + URLEncoder.encode(AuthConfig.googleRedirectUri(), StandardCharsets.UTF_8)
            + "&grant_type=authorization_code";

    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(URI.create("https://oauth2.googleapis.com/token"))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(payload))
            .build();

    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    JsonNode body = JSON_MAPPER.readTree(response.body());
    return body.path("access_token").asText(null);
  }

  private Map<String, String> fetchGoogleUserInfo(String accessToken)
      throws IOException, InterruptedException {
    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(URI.create("https://www.googleapis.com/oauth2/v3/userinfo"))
            .header("Authorization", "Bearer " + accessToken)
            .GET()
            .build();

    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    JsonNode body = JSON_MAPPER.readTree(response.body());

    Map<String, String> map = new HashMap<>();
    map.put("sub", body.path("sub").asText(null));
    map.put("email", body.path("email").asText(null));
    map.put("name", body.path("name").asText(null));
    map.put("picture", body.path("picture").asText(null));
    return map;
  }

  private Map<String, String> parseQuery(String query) {
    Map<String, String> map = new HashMap<>();
    if (query != null) {
      for (String param : query.split("&")) {
        String[] pair = param.split("=", 2);
        map.put(
            URLDecoder.decode(pair[0], StandardCharsets.UTF_8),
            pair.length > 1 ? URLDecoder.decode(pair[1], StandardCharsets.UTF_8) : "");
      }
    }
    return map;
  }
}
