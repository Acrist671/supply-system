package com.acrist.auth;

public record StoredUser(int id, String username, Role role, byte[] salt,
                         byte[] passwordHash, int hashIterations) {
    public AppUser publicView() {
        return new AppUser(id, username, role.value());
    }
}
