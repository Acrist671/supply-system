package com.acrist.server.auth;

import at.favre.lib.crypto.bcrypt.BCrypt;
import com.acrist.shared.auth.AppUser;
import com.acrist.shared.auth.LoginResponse;
import com.acrist.shared.auth.Role;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthServiceTest {
    private final MemoryRepository repository = new MemoryRepository();
    private final AuthService service = new AuthService(repository);

    @Test
    void logsInWithBcryptAndInvalidatesSessionOnLogout() throws SQLException {
        AppUser created = service.createUser("operator1", "correct horse battery", Role.OPERATOR);
        LoginResponse login = service.login("operator1", "correct horse battery");

        assertEquals(created, login.user());
        assertTrue(login.token().length() >= 40);
        assertTrue(repository.user.bcryptHash().startsWith("$2"));
        assertEquals(60, repository.user.bcryptHash().length());
        assertNotEquals("correct horse battery", repository.user.bcryptHash());
        assertTrue(BCrypt.verifyer().verify("correct horse battery".toCharArray(),
                repository.user.bcryptHash()).verified);
        assertEquals(created, service.requireUser("Bearer " + login.token()));
        assertThrows(UnauthorizedException.class,
                () -> service.login("operator1", "correct horse battery" + "x".repeat(73)));

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

    @Test
    void rejectsPasswordsExceedingBcryptLimit() {
        String password = "x".repeat(73);
        assertThrows(IllegalArgumentException.class,
                () -> service.createUser("newuser", password, Role.VIEWER));
        assertThrows(IllegalArgumentException.class,
                () -> service.createUser("newuser", "я".repeat(40), Role.VIEWER));
    }

    @Test
    void createsInitialAdminOnlyWhenThereAreNoUsers() throws SQLException {
        AuthService.BootstrapAccount account = service.ensureInitialAdmin(null, null).orElseThrow();

        assertEquals("admin", account.username());
        assertTrue(account.generatedPassword().length() >= 8);
        assertEquals(Role.ADMIN, repository.user.role());
        assertEquals("admin", service.login("admin", account.generatedPassword()).user().username());
        assertTrue(service.ensureInitialAdmin(null, null).isEmpty());
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
        public AppUser create(String username, Role role, String bcryptHash) {
            user = new StoredUser(1, username, role, bcryptHash);
            return user.publicView();
        }
    }
}
