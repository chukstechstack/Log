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

public final class RegisterHandler implements HttpHandler {
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
      String name = AuthHandlerSupport.text(body, "name");
      if (email == null
          || email.trim().length() > 254
          || !EMAIL_PATTERN.matcher(email.trim()).matches()) {
        AuthHandlerSupport.sendError(exchange, 400, "A valid email is required");
        return;
      }
      if (password == null || password.length() < 12 || password.length() > 1024) {
        AuthHandlerSupport.sendError(
            exchange, 400, "Password must be between 12 and 1024 characters");
        return;
      }

      String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
      String displayName = name == null || name.isBlank()
          ? normalizedEmail.substring(0, normalizedEmail.indexOf('@'))
          : name.trim();
      if (displayName.length() > 255) {
        AuthHandlerSupport.sendError(exchange, 400, "Name must be at most 255 characters");
        return;
      }

      User user = LocalAuthService.register(normalizedEmail, displayName, password.toCharArray());
      AuthHandlerSupport.sendSession(exchange, user);
    } catch (IllegalArgumentException e) {
      AuthHandlerSupport.sendError(exchange, 400, "Invalid JSON request");
    } catch (SQLException e) {
      if ("23505".equals(e.getSQLState())) {
        AuthHandlerSupport.sendError(exchange, 409, "An account with that email already exists");
      } else {
        System.err.println("[Auth Register] Database error (SQLState " + e.getSQLState() + ")");
        AuthHandlerSupport.sendError(exchange, 500, "Registration failed");
      }
    } catch (RuntimeException e) {
      System.err.println("[Auth Register] Unexpected error: " + e.getClass().getSimpleName());
      AuthHandlerSupport.sendError(exchange, 500, "Registration failed");
    }
  }

}
