package com.typeme.services;

import com.typeme.dao.UserDao;
import com.typeme.models.User;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Locale;

public final class LocalAuthService {
  private LocalAuthService() {}

  public static User register(String email, String name, char[] password) throws SQLException {
    try {
      return UserDao.createLocalUser(
          email.trim().toLowerCase(Locale.ROOT), name, PasswordHasher.hash(password));
    } finally {
      Arrays.fill(password, '\0');
    }
  }

  public static User authenticate(String email, char[] password) throws SQLException {
    try {
      String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
      String passwordHash = UserDao.findPasswordHashByEmail(normalizedEmail);
      if (!PasswordHasher.verify(password, passwordHash)) {
        return null;
      }
      return UserDao.findByEmail(normalizedEmail);
    } finally {
      Arrays.fill(password, '\0');
    }
  }
}
