package com.acrist.shared.auth;

public record LoginResponse(String token, AppUser user) {
}
