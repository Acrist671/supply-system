package com.acrist.server.auth;

import com.acrist.shared.auth.AppUser;
import com.acrist.shared.auth.Role;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public interface AuthRepository {
    long countUsers() throws SQLException;

    Optional<StoredUser> findByUsername(String username) throws SQLException;

    List<AppUser> findAll() throws SQLException;

    AppUser create(String username, Role role, String bcryptHash) throws SQLException;
}
