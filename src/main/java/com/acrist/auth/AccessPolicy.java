package com.acrist.auth;

public final class AccessPolicy {
    private AccessPolicy() {
    }

    public static void require(Role role, String method, String path) {
        if (path.startsWith("/api/auth/")) {
            return;
        }
        if ("/api/users".equals(path)) {
            if (role != Role.ADMIN) {
                throw new ForbiddenException("Только администратор может управлять пользователями");
            }
            return;
        }
        if ("GET".equals(method)) {
            return;
        }
        if ("DELETE".equals(method) && !role.canDelete()) {
            throw new ForbiddenException("Удаление доступно только администратору");
        }
        if (!role.canWrite()) {
            throw new ForbiddenException("Для этого действия недостаточно прав");
        }
    }
}
