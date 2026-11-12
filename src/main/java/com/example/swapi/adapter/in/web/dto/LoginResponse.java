package com.example.swapi.adapter.in.web.dto;

/** OAuth2-style token response; {@code expiresIn} is in seconds. */
public record LoginResponse(String accessToken, String tokenType, long expiresIn) {

    public static LoginResponse bearer(String token, long expiresInSeconds) {
        return new LoginResponse(token, "Bearer", expiresInSeconds);
    }
}
