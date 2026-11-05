package com.typeme.handlers;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.typeme.config.AuthConfig;
import com.typeme.services.SessionService;
//
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

public class GoogleLoginHandler implements HttpHandler {
  private static final SecureRandom SECURE_RANDOM = new SecureRandom();
  private static final int STATE_TTL_SECONDS = 300;

  @Override
  public void handle(HttpExchange exchange) throws IOException {
    if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
      exchange.getResponseHeaders().set("Allow", "GET");
      exchange.sendResponseHeaders(405, -1);
      return;
    }

    String clientId = AuthConfig.googleClientId();
    if (clientId == null
        || clientId.isBlank()
        || AuthConfig.googleClientSecret() == null
        || AuthConfig.googleClientSecret().isBlank()) {
      exchange.sendResponseHeaders(500, -1);
      return;
    }

    byte[] stateBytes = new byte[32];
    SECURE_RANDOM.nextBytes(stateBytes);
    String state = Base64.getUrlEncoder().withoutPadding().encodeToString(stateBytes);

    exchange
        .getResponseHeaders()
        .add(
            "Set-Cookie",
            SessionService.buildCookie(
                AuthConfig.stateCookieName(),
                state,
                STATE_TTL_SECONDS,
                AuthConfig.cookieSameSite()));

    String googleAuthUrl =
        "https://accounts.google.com/o/oauth2/v2/auth?client_id="
            + URLEncoder.encode(clientId, StandardCharsets.UTF_8)
            + "&redirect_uri="
            + URLEncoder.encode(AuthConfig.googleRedirectUri(), StandardCharsets.UTF_8)
            + "&response_type=code"
            + "&scope="
            + URLEncoder.encode("openid email profile", StandardCharsets.UTF_8)
            + "&state="
            + URLEncoder.encode(state, StandardCharsets.UTF_8);

    exchange.getResponseHeaders().set("Location", googleAuthUrl);
    exchange.sendResponseHeaders(302, -1);
  }
}
