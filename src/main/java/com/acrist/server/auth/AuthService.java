package com.acrist.server.auth;

import at.favre.lib.crypto.bcrypt.BCrypt;
import com.acrist.shared.auth.AppUser;
import com.acrist.shared.auth.LoginResponse;
import com.acrist.shared.auth.Role;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

public class AuthService {
    private static final int BCRYPT_COST = 12;
    private static final int BCRYPT_MAX_PASSWORD_BYTES = 72;
    private static final Duration MAX_SESSION_AGE = Duration.ofHours(8);
    private static final Duration IDLE_TIMEOUT = Duration.ofMinutes(30);
    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9._-]{3,50}");
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String DUMMY_HASH = bcryptHash("unused-dummy-password");

    private final AuthRepository repository;
    private final ConcurrentHashMap<String, Session> sessions = new ConcurrentHashMap<>();

    public AuthService(AuthRepository repository) {
        this.repository = repository;
    }

    public Optional<BootstrapAccount> ensureInitialAdmin(String username, String password) throws SQLException {
        if (repository.countUsers() != 0) {
            return Optional.empty();
        }

        String adminName = username == null || username.isBlank() ? "admin" : username;
        boolean generated = password == null || password.isBlank();
        String adminPassword = generated
                ? Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes(24)) : password;

        createUser(adminName, adminPassword, Role.ADMIN);

        return Optional.of(new BootstrapAccount(adminName, generated ? adminPassword : null));
    }

    public LoginResponse login(String username, String password) throws SQLException {
        Optional<StoredUser> found = username == null ? Optional.empty() : repository.findByUsername(username);
        StoredUser user = found.orElse(null);
        String candidate = password == null ? "" : password;
        boolean authenticated = verifyBcrypt(candidate, user == null ? DUMMY_HASH : user.bcryptHash());

        if (user == null || !authenticated) {
            throw new UnauthorizedException("Неверный логин или пароль");
        }

        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes(32));
        Instant now = Instant.now();
        AppUser publicUser = user.publicView();
        sessions.put(token, new Session(publicUser, now, now));

        return new LoginResponse(token, publicUser);
    }

    public AppUser requireUser(String authorization) {
        String token = bearerToken(authorization);
        Instant now = Instant.now();
        AtomicReference<AppUser> found = new AtomicReference<>();
        sessions.computeIfPresent(token, (key, session) -> {
            if (now.isAfter(session.createdAt().plus(MAX_SESSION_AGE))
                    || now.isAfter(session.lastSeen().plus(IDLE_TIMEOUT))) {
                return null;
            }
            found.set(session.user());

            return new Session(session.user(), session.createdAt(), now);
        });

        if (found.get() == null) {
            throw new UnauthorizedException("Войдите в систему заново");
        }

        return found.get();
    }

    public void logout(String authorization) {
        sessions.remove(bearerToken(authorization));
    }

    public List<AppUser> findUsers() throws SQLException {
        return repository.findAll();
    }

    public AppUser createUser(String username, String password, Role role) throws SQLException {
        if (username == null || !USERNAME.matcher(username).matches()) {
            throw new IllegalArgumentException("Логин: 3–50 символов, латиница, цифры, точка, дефис или подчёркивание");
        }

        if (password == null || password.length() < 8
                || password.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_PASSWORD_BYTES) {
            throw new IllegalArgumentException("Пароль должен содержать не менее 8 символов и не более 72 байт");
        }

        if (role == null) {
            throw new IllegalArgumentException("Укажите роль пользователя");
        }

        return repository.create(username, role, bcryptHash(password));
    }

    private static String bearerToken(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")
                || authorization.length() <= 7 || authorization.indexOf(' ', 7) >= 0) {
            throw new UnauthorizedException("Требуется вход в систему");
        }

        return authorization.substring(7);
    }

    private static String bcryptHash(String password) {
        char[] chars = password.toCharArray();
        try {
            return BCrypt.withDefaults().hashToString(BCRYPT_COST, chars);
        } finally {
            Arrays.fill(chars, '\0');
        }
    }

    private static boolean verifyBcrypt(String password, String hash) {
        if (password.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_PASSWORD_BYTES) {
            return false;
        }

        char[] chars = password.toCharArray();
        try {
            return BCrypt.verifyer().verify(chars, hash).verified;
        } finally {
            Arrays.fill(chars, '\0');
        }
    }

    private static byte[] randomBytes(int length) {
        byte[] value = new byte[length];
        RANDOM.nextBytes(value);

        return value;
    }

    private record Session(AppUser user, Instant createdAt, Instant lastSeen) {
    }

    public record BootstrapAccount(String username, String generatedPassword) {
    }
}
