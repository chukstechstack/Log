package com.typeme.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.typeme.config.AuthConfig;
import com.typeme.models.User;
import com.typeme.services.SessionService;
import java.io.IOException;
import java.sql.SQLException;

public class SessionHandler implements HttpHandler {
  private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

  @Override
  public void handle(HttpExchange exchange) throws IOException {
    AuthHandlerSupport.addCorsHeaders(exchange);
    if (AuthHandlerSupport.handleOptions(exchange, "GET")) {
      return;
    }
    if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
      exchange.getResponseHeaders().set("Allow", "GET, OPTIONS");
      exchange.sendResponseHeaders(405, -1);
      return;
    }

    try {
      User user = SessionService.getCurrentUser(exchange);
      if (user == null) {
        exchange
            .getResponseHeaders()
            .add(
                "Set-Cookie",
                SessionService.buildCookie(
                    AuthConfig.sessionCookieName(), "", 0, AuthConfig.cookieSameSite()));
        exchange.sendResponseHeaders(401, -1);
        return;
      }

      ObjectNode json = JSON_MAPPER.createObjectNode();
      json.put("id", user.getId());
      json.put("email", user.getEmail());
      json.put("name", user.getName());
      json.put("avatarUrl", user.getAvatarUrl());
      json.put("admin", user.isAdmin());

      byte[] bytes = JSON_MAPPER.writeValueAsBytes(json);
      exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
      exchange.sendResponseHeaders(200, bytes.length);
      exchange.getResponseBody().write(bytes);
    } catch (SQLException e) {
      System.err.println("[Auth Session] Database error (SQLState " + e.getSQLState() + ")");
      AuthHandlerSupport.sendError(exchange, 500, "Unable to retrieve session");
    }
  }
}
