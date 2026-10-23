package com.acrist.auth;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public interface AuthRepository {
    long countUsers() throws SQLException;

    Optional<StoredUser> findByUsername(String username) throws SQLException;

    List<AppUser> findAll() throws SQLException;

    AppUser create(String username, Role role, byte[] salt,
                   byte[] passwordHash, int hashIterations) throws SQLException;
}
