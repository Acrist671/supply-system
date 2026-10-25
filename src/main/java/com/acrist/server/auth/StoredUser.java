package com.acrist.server.auth;

import com.acrist.shared.auth.AppUser;
import com.acrist.shared.auth.Role;

public record StoredUser(int id, String username, Role role, String bcryptHash) {
    public AppUser publicView() {
        return new AppUser(id, username, role.value());
    }
}
