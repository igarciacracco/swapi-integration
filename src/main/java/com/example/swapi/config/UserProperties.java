package com.example.swapi.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/** Users seeded from configuration, as BCrypt hashes supplied by environment variables. */
@Validated
@ConfigurationProperties(prefix = "app")
public record UserProperties(@NotEmpty List<User> users) {

    public record User(@NotBlank String username, @NotBlank String passwordHash) {}
}
