package com.example.swapi.domain;

import java.time.Instant;
import java.util.UUID;

/** A registered user. {@code toString} masks the hash so it never reaches a log. */
public record User(UUID id, String username, String passwordHash, Instant createdAt) {

    public static User newUser(String username, String passwordHash) {
        return new User(UUID.randomUUID(), username, passwordHash, Instant.now());
    }

    @Override
    public String toString() {
        return "User[id=%s, username=%s, passwordHash=***]".formatted(id, username);
    }
}
