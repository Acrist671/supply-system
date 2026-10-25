package com.acrist.server.config;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public record DatabaseConfig(String url, String user, String password) {
    public static DatabaseConfig load(Path envFile) throws Exception {
        Properties properties = new Properties();

        try (Reader reader = Files.newBufferedReader(envFile, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }

        String url = value("FLYWAY_URL", "flyway.url", properties);
        String user = value("FLYWAY_USER", "flyway.user", properties);
        String password = value("FLYWAY_PASSWORD", "flyway.password", properties);

        if (url == null || url.isBlank() || user == null || user.isBlank()) {
            throw new IllegalStateException("Укажите flyway.url и flyway.user в .env");
        }

        return new DatabaseConfig(url, user, password == null ? "" : password);
    }

    private static String value(String environmentName, String propertyName, Properties properties) {
        String environmentValue = System.getenv(environmentName);

        return environmentValue != null ? environmentValue : properties.getProperty(propertyName);
    }
}
