package com.acrist.auth;

import com.acrist.config.DatabaseConfig;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class JdbcAuthRepository implements AuthRepository {
    private final DatabaseConfig config;

    public JdbcAuthRepository(DatabaseConfig config) {
        this.config = config;
    }

    @Override
    public long countUsers() throws SQLException {
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM app_users");
             ResultSet result = statement.executeQuery()) {
            result.next();

            return result.getLong(1);
        }
    }

    @Override
    public Optional<StoredUser> findByUsername(String username) throws SQLException {
        String sql = "SELECT id, username, role, password_salt, password_hash, hash_iterations "
                + "FROM app_users WHERE username = ?";

        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);

            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }

                return Optional.of(new StoredUser(result.getInt("id"), result.getString("username"),
                        Role.fromValue(result.getString("role")), result.getBytes("password_salt"),
                        result.getBytes("password_hash"), result.getInt("hash_iterations")));
            }
        }
    }

    @Override
    public List<AppUser> findAll() throws SQLException {
        List<AppUser> users = new ArrayList<>();

        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT id, username, role FROM app_users ORDER BY id");
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                users.add(new AppUser(result.getInt("id"), result.getString("username"),
                        result.getString("role")));
            }
        }

        return users;
    }

    @Override
    public AppUser create(String username, Role role, byte[] salt,
                          byte[] passwordHash, int hashIterations) throws SQLException {
        String sql = "INSERT INTO app_users (username, role, password_salt, password_hash, hash_iterations) "
                + "VALUES (?, ?, ?, ?, ?) RETURNING id";

        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            statement.setString(2, role.value());
            statement.setBytes(3, salt);
            statement.setBytes(4, passwordHash);
            statement.setInt(5, hashIterations);

            try (ResultSet result = statement.executeQuery()) {
                result.next();

                return new AppUser(result.getInt("id"), username, role.value());
            }
        }
    }

    private Connection openConnection() throws SQLException {
        return DriverManager.getConnection(config.url(), config.user(), config.password());
    }
}
