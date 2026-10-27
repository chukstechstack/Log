package com.typeme.config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseConfig {
  private static final String URL =
      environmentOrDefault("DATABASE_URL", "jdbc:postgresql://localhost:5433/typeme_db");
  private static final String USER = environmentOrDefault("DATABASE_USER", "typeme_user");
  private static final String PASSWORD =
      environmentOrDefault("DATABASE_PASSWORD", "typeme_password");

  public static Connection getConnection() throws SQLException {
    try {
      Class.forName("org.postgresql.Driver");
    } catch (ClassNotFoundException e) {
      throw new SQLException("PostgreSQL JDBC Driver not found in classpath.", e);
    }
    return DriverManager.getConnection(URL, USER, PASSWORD);
  }

  private static String environmentOrDefault(String name, String defaultValue) {
    String value = System.getenv(name);
    return value == null || value.isBlank() ? defaultValue : value.trim();
  }
}
