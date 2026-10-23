package com.acrist.auth;

import java.util.Locale;

public enum Role {
    ADMIN, OPERATOR, VIEWER;

    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Role fromValue(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Укажите роль пользователя");
        }
        try {
            return Role.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Неизвестная роль пользователя");
        }
    }

    public boolean canWrite() {
        return this == ADMIN || this == OPERATOR;
    }

    public boolean canDelete() {
        return this == ADMIN;
    }
}
