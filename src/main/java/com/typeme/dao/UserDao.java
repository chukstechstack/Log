package com.typeme.dao;

import com.typeme.config.DatabaseConfig;
import com.typeme.models.User;
import java.sql.*;
import java.time.LocalDateTime;

public class UserDao {

  public static User findOrCreateByGoogleId(
      String googleId, String email, String name, String avatarUrl) throws SQLException {
    String selectSql =
        "SELECT id, google_id, email, name, avatar_url, is_admin, created_at FROM users WHERE"
            + " google_id = ?";
    String insertSql =
        "INSERT INTO users (google_id, email, name, avatar_url) VALUES (?, ?, ?, ?) RETURNING id,"
            + " google_id, email, name, avatar_url, is_admin, created_at";

    try (Connection conn = DatabaseConfig.getConnection()) {
      // 1. Try to find existing user
      try (PreparedStatement pstmt = conn.prepareStatement(selectSql)) {
        pstmt.setString(1, googleId);
        try (ResultSet rs = pstmt.executeQuery()) {
          if (rs.next()) {
            return mapRowToUser(rs);
          }
        }
      }

      // 2. If not found, insert new user
      try (PreparedStatement pstmt = conn.prepareStatement(insertSql)) {
        pstmt.setString(1, googleId);
        pstmt.setString(2, email);
        pstmt.setString(3, name);
        pstmt.setString(4, avatarUrl);
        try (ResultSet rs = pstmt.executeQuery()) {
          if (rs.next()) {
            return mapRowToUser(rs);
          }
        }
      }
    }
    throw new SQLException("Failed to find or create user for google_id: " + googleId);
  }

  private static User mapRowToUser(ResultSet rs) throws SQLException {
    Timestamp createdAtTs = rs.getTimestamp("created_at");
    LocalDateTime createdAt = createdAtTs != null ? createdAtTs.toLocalDateTime() : null;
    return new User(
        rs.getInt("id"),
        rs.getString("google_id"),
        rs.getString("email"),
        rs.getString("name"),
        rs.getString("avatar_url"),
        rs.getBoolean("is_admin"),
        createdAt);
  }
}
