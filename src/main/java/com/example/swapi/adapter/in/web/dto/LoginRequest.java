package com.example.swapi.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Only presence and length are checked, so validation reveals nothing about stored credentials. */
public record LoginRequest(
        @NotBlank(message = "username must not be blank")
        @Size(max = 100, message = "username must be at most 100 characters") String username,

        @NotBlank(message = "password must not be blank")
        @Size(max = 200, message = "password must be at most 200 characters") String password) {}
