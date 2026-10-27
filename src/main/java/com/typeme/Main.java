package com.typeme;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.typeme.config.DatabaseConfig;
import com.typeme.config.SchemaInitializer;
import com.typeme.handlers.GoogleAuthHandler;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.sql.Connection;
import java.sql.SQLException;

public class Main {
  public static void main(String[] args) throws IOException {
    try (Connection conn = DatabaseConfig.getConnection()) {
      conn.isValid(1);
      System.out.println("[Database] Successfully connected to PostgreSQL container!");
      SchemaInitializer.initializeSchema();
    } catch (SQLException e) {
      throw new IOException("Database initialization failed; refusing to start without persistence", e);
    }

    int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "7070"));
    HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

    // Health check endpoint
    server.createContext(
        "/health",
        new HttpHandler() {
          @Override
          public void handle(HttpExchange exchange) throws IOException {
            String response = "TypeMe Backend is running!";
            exchange.sendResponseHeaders(200, response.getBytes().length);
            OutputStream os = exchange.getResponseBody();
            os.write(response.getBytes());
            os.close();
          }
        });

    // Google OAuth endpoints
    GoogleAuthHandler googleAuthHandler = new GoogleAuthHandler();
    server.createContext("/auth", googleAuthHandler);

    server.setExecutor(null);
    server.start();
    System.out.println("[Server] TypeMe HTTP server started on port http://localhost:" + port);
  }
}
