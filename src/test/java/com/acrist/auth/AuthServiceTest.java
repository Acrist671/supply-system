package com.acrist.auth;

import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthServiceTest {
    private final MemoryRepository repository = new MemoryRepository();
    private final AuthService service = new AuthService(repository);

    @Test
    void logsInWithHashedPasswordAndInvalidatesSessionOnLogout() throws SQLException {
        AppUser created = service.createUser("operator1", "correct horse battery", Role.OPERATOR);
        LoginResponse login = service.login("operator1", "correct horse battery");

        assertEquals(created, login.user());
        assertTrue(login.token().length() >= 40);
        assertFalse(repository.user.passwordHash().length == 0);
        assertNotEquals("correct horse battery", new String(repository.user.passwordHash()));
        assertEquals(created, service.requireUser("Bearer " + login.token()));

        service.logout("Bearer " + login.token());
        assertThrows(UnauthorizedException.class,
                () -> service.requireUser("Bearer " + login.token()));
    }

    @Test
    void rejectsIncorrectPasswordAndMissingBearerToken() throws SQLException {
        service.createUser("viewer1", "valid password", Role.VIEWER);

        assertThrows(UnauthorizedException.class, () -> service.login("viewer1", "wrong password"));
        assertThrows(UnauthorizedException.class, () -> service.requireUser(null));
    }

    private static class MemoryRepository implements AuthRepository {
        private StoredUser user;

        @Override
        public long countUsers() {
            return user == null ? 0 : 1;
        }

        @Override
        public Optional<StoredUser> findByUsername(String username) {
            return user != null && user.username().equals(username) ? Optional.of(user) : Optional.empty();
        }

        @Override
        public List<AppUser> findAll() {
            List<AppUser> result = new ArrayList<>();
            if (user != null) {
                result.add(user.publicView());
            }
            return result;
        }

        @Override
        public AppUser create(String username, Role role, byte[] salt,
                              byte[] passwordHash, int hashIterations) {
            user = new StoredUser(1, username, role, salt, passwordHash, hashIterations);
            return user.publicView();
        }
    }
}
