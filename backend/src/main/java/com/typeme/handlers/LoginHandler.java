package com.typeme.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.typeme.models.User;
import com.typeme.services.LocalAuthService;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Locale;
import java.util.regex.Pattern;

public final class LoginHandler implements HttpHandler {
  private static final Pattern EMAIL_PATTERN =
      Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

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
      JsonNode body = AuthHandlerSupport.readJson(exchange);
      String email = AuthHandlerSupport.text(body, "email");
      String password = AuthHandlerSupport.text(body, "password");
      if (email == null || email.length() > 254 || !EMAIL_PATTERN.matcher(email.trim()).matches()
          || password == null || password.length() > 1024) {
        AuthHandlerSupport.sendError(exchange, 401, "Invalid email or password");
        return;
      }

      User user =
          LocalAuthService.authenticate(email.trim().toLowerCase(Locale.ROOT), password.toCharArray());
      if (user == null) {
        AuthHandlerSupport.sendError(exchange, 401, "Invalid email or password");
        return;
      }
      AuthHandlerSupport.sendSession(exchange, user);
    } catch (IllegalArgumentException e) {
      AuthHandlerSupport.sendError(exchange, 400, "Invalid JSON request");
    } catch (SQLException e) {
      System.err.println("[Auth Login] Database error (SQLState " + e.getSQLState() + ")");
      AuthHandlerSupport.sendError(exchange, 500, "Login failed");
    } catch (RuntimeException e) {
      System.err.println("[Auth Login] Unexpected error: " + e.getClass().getSimpleName());
      AuthHandlerSupport.sendError(exchange, 500, "Login failed");
    }
  }

}
