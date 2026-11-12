package com.example.swapi.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Validated before hashing, so a malformed request never costs a BCrypt round.
 * {@code RegistrationService} enforces the same rules for other callers.
 */
public record RegisterRequest(
        @NotBlank(message = "username must not be blank")
        @Pattern(regexp = "^[A-Za-z0-9_.-]{3,50}$",
                message = "username must be 3-50 characters of letters, digits, _ . or -")
        String username,

        @NotBlank(message = "password must not be blank")
        @Size(min = 12, max = 72,
                message = "password must be between 12 and 72 characters")
        String password) {}
