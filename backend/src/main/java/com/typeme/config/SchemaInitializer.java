package com.typeme.config;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class SchemaInitializer {

    public static void initializeSchema() throws SQLException {
        // Base users table definition supporting both Google and local auth
        String createUsersTable =
            "CREATE TABLE IF NOT EXISTS users (" +
            "id SERIAL PRIMARY KEY, " +
            "google_id VARCHAR(255) UNIQUE, " + // Nullable for local auth users
            "email VARCHAR(255) UNIQUE NOT NULL, " +
            "password_hash VARCHAR(255), " +    // Stores hashed password for local users
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
            
            // 1. Create base tables if they don't exist
            statement.execute(createUsersTable);
            statement.execute(createSessionsTable);

            // 2. Migration guard: Drop NOT NULL constraint on google_id if upgrading an existing DB
            statement.execute(
                "DO $$ BEGIN "
                + "IF EXISTS (SELECT 1 FROM information_schema.columns "
                + "WHERE table_schema = current_schema() AND table_name = 'users' "
                + "AND column_name = 'google_id' AND is_nullable = 'NO') THEN "
                + "ALTER TABLE users ALTER COLUMN google_id DROP NOT NULL; "
                + "END IF; END $$");

            // 3. Migration guard: Add password_hash column if upgrading an existing DB
            statement.execute(
                "DO $$ BEGIN "
                + "IF NOT EXISTS (SELECT 1 FROM information_schema.columns "
                + "WHERE table_schema = current_schema() AND table_name = 'users' "
                + "AND column_name = 'password_hash') THEN "
                + "ALTER TABLE users ADD COLUMN password_hash VARCHAR(255); "
                + "END IF; END $$");

            // 4. Existing migration guard: Ensure session expires_at uses TIMESTAMPTZ
            statement.execute(
                "DO $$ BEGIN "
                + "IF (SELECT data_type FROM information_schema.columns "
                + "WHERE table_schema = current_schema() AND table_name = 'sessions' "
                + "AND column_name = 'expires_at') = 'timestamp without time zone' THEN "
                + "ALTER TABLE sessions ALTER COLUMN expires_at TYPE TIMESTAMPTZ "
                + "USING expires_at AT TIME ZONE 'UTC'; "
                + "END IF; END $$");

            // 5. Create indexes
            statement.execute(
                "CREATE INDEX IF NOT EXISTS idx_sessions_user_id ON sessions(user_id)");
            statement.execute(
                "CREATE INDEX IF NOT EXISTS idx_sessions_expires_at ON sessions(expires_at)");
            statement.execute(
                "CREATE INDEX IF NOT EXISTS idx_users_email ON users(email)");

            System.out.println("[Database] Schema initialized successfully.");
        }
    }
}