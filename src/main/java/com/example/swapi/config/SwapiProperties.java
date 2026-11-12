package com.example.swapi.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/** SWAPI client settings, validated at startup. Tests point the base URL at a stub. */
@Validated
@ConfigurationProperties(prefix = "swapi")
public record SwapiProperties(
        @NotBlank String baseUrl,
        @NotNull Duration connectTimeout,
        @NotNull Duration readTimeout,
        @NotBlank String userAgent) {}
