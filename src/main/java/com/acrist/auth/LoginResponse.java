package com.acrist.auth;

public record LoginResponse(String token, AppUser user) {
}
