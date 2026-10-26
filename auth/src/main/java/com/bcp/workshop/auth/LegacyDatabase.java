package com.bcp.workshop.auth;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import io.quarkus.elytron.security.common.BcryptUtil;
import org.jboss.logging.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class LegacyDatabase {
    private static final String DB_URL = System.getenv().getOrDefault("AUTH_DB_URL", "jdbc:sqlite:./data/workshop.db");
    private static final Logger LOG = Logger.getLogger(LegacyDatabase.class);

    @PostConstruct
    void initialize() {
        try {
            Files.createDirectories(Path.of("data"));
            initializeDatabase();
        } catch (Exception error) {
            LOG.error("Unable to initialize auth database", error);
        }
    }

    private void initializeDatabase() throws Exception {
        try (Connection connection = DriverManager.getConnection(DB_URL)) {
            connection.setAutoCommit(false);
            try {
                createSchema(connection);
                insertSeedUser(connection, "student", "Workshop123!", "Workshop Student", "STUDENT");
                insertSeedUser(connection, "trainer", "Trainer123!", "Workshop Trainer", "TRAINER");
                migrateLegacyPasswords(connection);
                connection.commit();
            } catch (Exception error) {
                connection.rollback();
                throw error;
            }
        }
    }

    private void createSchema(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS legacy_users (id INTEGER PRIMARY KEY AUTOINCREMENT, username TEXT UNIQUE, password TEXT NOT NULL, display_name TEXT NOT NULL, role TEXT NOT NULL)");
        }
    }

    private void insertSeedUser(Connection connection, String username, String password, String displayName, String role) throws Exception {
        String sql = "INSERT OR IGNORE INTO legacy_users(username,password,display_name,role) VALUES (?,?,?,?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            statement.setString(2, BcryptUtil.bcryptHash(password));
            statement.setString(3, displayName);
            statement.setString(4, role);
            statement.executeUpdate();
        }
    }

    private void migrateLegacyPasswords(Connection connection) throws Exception {
        List<StoredPassword> storedPasswords = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT id, password FROM legacy_users")) {
            while (result.next()) {
                storedPasswords.add(new StoredPassword(result.getLong("id"), result.getString("password")));
            }
        }

        String sql = "UPDATE legacy_users SET password=? WHERE id=? AND password=?";
        try (PreparedStatement update = connection.prepareStatement(sql)) {
            for (StoredPassword storedPassword : storedPasswords) {
                if (isBcryptHash(storedPassword.value())) {
                    continue;
                }
                update.setString(1, BcryptUtil.bcryptHash(storedPassword.value()));
                update.setLong(2, storedPassword.userId());
                update.setString(3, storedPassword.value());
                update.executeUpdate();
            }
        }
    }

    private boolean isBcryptHash(String password) {
        return password.startsWith("$2a$") || password.startsWith("$2b$") || password.startsWith("$2y$");
    }

    public UserRecord findByCredentials(String username, String password) {
        String sql = "SELECT id, username, password, display_name, role FROM legacy_users WHERE username=?";
        try (Connection connection = DriverManager.getConnection(DB_URL);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next() || !BcryptUtil.matches(password, result.getString("password"))) {
                    return null;
                }
                return new UserRecord(result.getLong("id"), result.getString("username"), result.getString("display_name"), result.getString("role"));
            }
        } catch (Exception error) {
            LOG.error("Unable to query auth database", error);
        }
        return null;
    }

    private record StoredPassword(long userId, String value) {}
}
