package com.typeme.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.typeme.config.AuthConfig;
import com.typeme.models.User;
import com.typeme.services.SessionService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

final class AuthHandlerSupport {
  private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
  private static final int MAX_REQUEST_BYTES = 16 * 1024;

  private AuthHandlerSupport() {}

  static void addCorsHeaders(HttpExchange exchange) {
    String origin = exchange.getRequestHeaders().getFirst("Origin");
    if (AuthConfig.frontendOrigin().equals(origin)) {
      exchange.getResponseHeaders().set("Access-Control-Allow-Origin", origin);
      exchange.getResponseHeaders().set("Access-Control-Allow-Credentials", "true");
      exchange.getResponseHeaders().set("Vary", "Origin");
    }
  }

  static boolean handleOptions(HttpExchange exchange, String methods) throws IOException {
    if (!"OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
      return false;
    }
    addCorsHeaders(exchange);
    exchange.getResponseHeaders().set("Access-Control-Allow-Methods", methods + ", OPTIONS");
    exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
    exchange.sendResponseHeaders(204, -1);
    return true;
  }

  static JsonNode readJson(HttpExchange exchange) throws IOException {
    byte[] body = exchange.getRequestBody().readNBytes(MAX_REQUEST_BYTES + 1);
    if (body.length > MAX_REQUEST_BYTES) {
      throw new IllegalArgumentException("Request body is too large");
    }
    JsonNode json;
    try {
      json = JSON_MAPPER.readTree(new String(body, StandardCharsets.UTF_8));
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Invalid JSON", e);
    }
    if (json == null || !json.isObject()) {
      throw new IllegalArgumentException("JSON object required");
    }
    return json;
  }

  static void sendJson(HttpExchange exchange, int status, ObjectNode json) throws IOException {
    byte[] body = JSON_MAPPER.writeValueAsBytes(json);
    exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
    exchange.getResponseHeaders().set("Cache-Control", "no-store");
    exchange.sendResponseHeaders(status, body.length);
    exchange.getResponseBody().write(body);
    exchange.close();
  }

  static void sendError(HttpExchange exchange, int status, String message) throws IOException {
    ObjectNode error = JSON_MAPPER.createObjectNode();
    error.put("error", message);
    sendJson(exchange, status, error);
  }

  static void sendSession(HttpExchange exchange, User user) throws IOException, java.sql.SQLException {
    String token = SessionService.createForUser(user.getId());
    int maxAge = Math.toIntExact(AuthConfig.sessionTtl().getSeconds());
    exchange.getResponseHeaders().add(
        "Set-Cookie",
        SessionService.buildCookie(
            AuthConfig.sessionCookieName(), token, maxAge, AuthConfig.cookieSameSite()));
    ObjectNode json = JSON_MAPPER.createObjectNode();
    json.put("id", user.getId());
    json.put("email", user.getEmail());
    json.put("name", user.getName());
    json.put("avatarUrl", user.getAvatarUrl());
    json.put("admin", user.isAdmin());
    sendJson(exchange, 200, json);
  }

  static String text(JsonNode body, String field) {
    JsonNode value = body.get(field);
    return value != null && value.isTextual() ? value.textValue() : null;
  }
}
