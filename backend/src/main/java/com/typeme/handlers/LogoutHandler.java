package com.typeme.handlers;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.typeme.config.AuthConfig;
import com.typeme.services.SessionService;
import java.io.IOException;

public class LogoutHandler implements HttpHandler {
  @Override
  public void handle(HttpExchange exchange) throws IOException {
    if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
      exchange.getResponseHeaders().set("Allow", "POST");
      exchange.sendResponseHeaders(405, -1);
      return;
    }

    try {
      SessionService.revokeCurrentSession(exchange);
      exchange.getResponseHeaders().add(
          "Set-Cookie",
          SessionService.buildCookie(AuthConfig.sessionCookieName(), "", 0, AuthConfig.cookieSameSite())
      );
      exchange.sendResponseHeaders(204, -1);
    } catch (Exception e) {
      exchange.sendResponseHeaders(500, -1);
    }
  }
}