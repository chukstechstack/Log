package com.typeme.dao;

import com.typeme.config.DatabaseConfig;
import com.typeme.models.User;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public final class SessionDao {
  private SessionDao() {}

  public static void create(int userId, String tokenHash, Instant expiresAt) throws SQLException {
    String cleanupSql = "DELETE FROM sessions WHERE expires_at <= CURRENT_TIMESTAMP";
    String insertSql = "INSERT INTO sessions (id, user_id, expires_at) VALUES (?, ?, ?)";

    try (Connection connection = DatabaseConfig.getConnection()) {
      try (PreparedStatement cleanup = connection.prepareStatement(cleanupSql)) {
        cleanup.executeUpdate();
      }
      try (PreparedStatement insert = connection.prepareStatement(insertSql)) {
        insert.setString(1, tokenHash);
        insert.setInt(2, userId);
        insert.setObject(3, OffsetDateTime.ofInstant(expiresAt, ZoneOffset.UTC));
        insert.executeUpdate();
      }
    }
  }

  public static User findUser(String tokenHash) throws SQLException {
    String sql =
        "SELECT u.id, u.google_id, u.email, u.name, u.avatar_url, u.is_admin, u.created_at "
            + "FROM sessions s JOIN users u ON u.id = s.user_id "
            + "WHERE s.id = ? AND s.expires_at > CURRENT_TIMESTAMP";

    try (Connection connection = DatabaseConfig.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, tokenHash);
      try (ResultSet result = statement.executeQuery()) {
        if (!result.next()) {
          return null;
        }
        Timestamp createdAtTimestamp = result.getTimestamp("created_at");
        return new User(
            result.getInt("id"),
            result.getString("google_id"),
            result.getString("email"),
            result.getString("name"),
            result.getString("avatar_url"),
            result.getBoolean("is_admin"),
            createdAtTimestamp == null ? null : createdAtTimestamp.toLocalDateTime());
      }
    }
  }

  public static void delete(String tokenHash) throws SQLException {
    try (Connection connection = DatabaseConfig.getConnection();
        PreparedStatement statement =
            connection.prepareStatement("DELETE FROM sessions WHERE id = ?")) {
      statement.setString(1, tokenHash);
      statement.executeUpdate();
    }
  }
}
