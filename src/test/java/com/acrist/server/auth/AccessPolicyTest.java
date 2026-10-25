package com.acrist.server.auth;

import com.acrist.shared.auth.Role;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AccessPolicyTest {
    @Test
    void viewerCanReadButCannotChangeData() {
        assertDoesNotThrow(() -> AccessPolicy.require(Role.VIEWER, "GET", "/api/products"));
        assertThrows(ForbiddenException.class,
                () -> AccessPolicy.require(Role.VIEWER, "POST", "/api/orders"));
    }

    @Test
    void operatorCanWorkButCannotDeleteOrManageUsers() {
        assertDoesNotThrow(() -> AccessPolicy.require(Role.OPERATOR, "POST", "/api/orders/1/start"));
        assertThrows(ForbiddenException.class,
                () -> AccessPolicy.require(Role.OPERATOR, "DELETE", "/api/orders/1"));
        assertThrows(ForbiddenException.class,
                () -> AccessPolicy.require(Role.OPERATOR, "GET", "/api/users"));
    }

    @Test
    void adminCanManageUsersAndDelete() {
        assertDoesNotThrow(() -> AccessPolicy.require(Role.ADMIN, "POST", "/api/users"));
        assertDoesNotThrow(() -> AccessPolicy.require(Role.ADMIN, "DELETE", "/api/products/1"));
    }
}
