package com.typeme.handlers;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.typeme.config.AuthConfig;
import com.typeme.services.SessionService;
import java.io.IOException;
import java.sql.SQLException;

public class LogoutHandler implements HttpHandler {
  @Override
  public void handle(HttpExchange exchange) throws IOException {
    AuthHandlerSupport.addCorsHeaders(exchange);
    if (AuthHandlerSupport.handleOptions(exchange, "POST")) {
      return;
    }
    if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
      exchange.getResponseHeaders().set("Allow", "POST, OPTIONS");
      exchange.sendResponseHeaders(405, -1);
      return;
    }

    try {
      SessionService.revokeCurrentSession(exchange);
      exchange
          .getResponseHeaders()
          .add(
              "Set-Cookie",
              SessionService.buildCookie(
                  AuthConfig.sessionCookieName(), "", 0, AuthConfig.cookieSameSite()));
      exchange.sendResponseHeaders(204, -1);
      exchange.close();
    } catch (SQLException e) {
      System.err.println("[Auth Logout] Database error (SQLState " + e.getSQLState() + ")");
      AuthHandlerSupport.sendError(exchange, 500, "Logout failed");
    }
  }
}