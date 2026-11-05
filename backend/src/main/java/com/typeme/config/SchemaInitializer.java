package com.typeme.config;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class SchemaInitializer {

    public static void initializeSchema() throws SQLException {
        String createUsersTable =
            "CREATE TABLE IF NOT EXISTS users (" +
            "id SERIAL PRIMARY KEY, " +
            "google_id VARCHAR(255) UNIQUE NOT NULL, " +
            "email VARCHAR(255) UNIQUE NOT NULL, " +
            "name VARCHAR(255), " +
            "avatar_url TEXT, " +
            "is_admin BOOLEAN DEFAULT FALSE, " +
            "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)";

        String createSessionsTable =
            "CREATE TABLE IF NOT EXISTS sessions (" +
            "id VARCHAR(64) PRIMARY KEY, " +
            "user_id INT NOT NULL REFERENCES users(id) ON DELETE CASCADE, " +
            "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
            "expires_at TIMESTAMPTZ NOT NULL)";

        try (Connection connection = DatabaseConfig.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(createUsersTable);
            statement.execute(createSessionsTable);
            statement.execute(
                "DO $$ BEGIN "
                    + "IF (SELECT data_type FROM information_schema.columns "
                    + "WHERE table_schema = current_schema() AND table_name = 'sessions' "
                    + "AND column_name = 'expires_at') = 'timestamp without time zone' THEN "
                    + "ALTER TABLE sessions ALTER COLUMN expires_at TYPE TIMESTAMPTZ "
                    + "USING expires_at AT TIME ZONE 'UTC'; "
                    + "END IF; END $$");
            statement.execute(
                "CREATE INDEX IF NOT EXISTS idx_sessions_user_id ON sessions(user_id)");
            statement.execute(
                "CREATE INDEX IF NOT EXISTS idx_sessions_expires_at ON sessions(expires_at)");
            System.out.println("[Database] Schema is ready.");
        }
    }

}