package com.acrist.auth;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
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
    private static final int HASH_ITERATIONS = 600_000;
    private static final int HASH_BITS = 256;
    private static final Duration MAX_SESSION_AGE = Duration.ofHours(8);
    private static final Duration IDLE_TIMEOUT = Duration.ofMinutes(30);
    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9._-]{3,50}");
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final byte[] DUMMY_SALT = randomBytes(16);

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
        byte[] candidate = hash(password == null ? "" : password,
                user == null ? DUMMY_SALT : user.salt(),
                user == null ? HASH_ITERATIONS : user.hashIterations());

        if (user == null || !MessageDigest.isEqual(candidate, user.passwordHash())) {
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
        if (password == null || password.length() < 8 || password.length() > 128) {
            throw new IllegalArgumentException("Пароль должен содержать от 8 до 128 символов");
        }
        if (role == null) {
            throw new IllegalArgumentException("Укажите роль пользователя");
        }
        byte[] salt = randomBytes(16);

        return repository.create(username, role, salt, hash(password, salt, HASH_ITERATIONS), HASH_ITERATIONS);
    }

    private static String bearerToken(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")
                || authorization.length() <= 7 || authorization.indexOf(' ', 7) >= 0) {
            throw new UnauthorizedException("Требуется вход в систему");
        }

        return authorization.substring(7);
    }

    private static byte[] hash(String password, byte[] salt, int iterations) {
        char[] chars = password.toCharArray();
        PBEKeySpec spec = new PBEKeySpec(chars, salt, iterations, HASH_BITS);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec).getEncoded();
        } catch (Exception exception) {
            throw new IllegalStateException("Не удалось обработать пароль", exception);
        } finally {
            spec.clearPassword();
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
