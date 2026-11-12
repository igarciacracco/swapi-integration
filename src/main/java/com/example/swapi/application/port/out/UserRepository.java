package com.example.swapi.application.port.out;

import com.example.swapi.domain.User;

import java.util.Optional;

/** Output port for user storage. */
public interface UserRepository {

    /** Case-insensitive, matching the unique index. */
    Optional<User> findByUsername(String username);

    /**
     * @throws com.example.swapi.domain.UsernameTakenException if the username is taken, including
     *         by a concurrent insert
     */
    User save(User user);
}
