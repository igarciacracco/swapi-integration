package com.example.swapi.domain;

/** Raised from the unique-constraint violation, so concurrent sign-ups cannot both succeed. */
public class UsernameTakenException extends RuntimeException {

    private final String username;

    public UsernameTakenException(String username) {
        super("Username '%s' is already taken".formatted(username));
        this.username = username;
    }

    public String username() {
        return username;
    }
}
