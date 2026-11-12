package com.example.swapi.application.port.in;

/** Input port for account creation, separate from the catalog queries. */
public interface RegisterUser {

    /**
     * @return the stored username; no token, the client logs in next
     * @throws IllegalArgumentException if the username or password breaks the rules
     * @throws com.example.swapi.domain.UsernameTakenException if the username is taken
     */
    String register(String username, String rawPassword);
}
