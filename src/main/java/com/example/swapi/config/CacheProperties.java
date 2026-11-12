package com.example.swapi.config;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/** Cache size and TTL, configurable without a code change. */
@Validated
@ConfigurationProperties(prefix = "app.cache")
public record CacheProperties(
        @Positive long maximumSize,
        @NotNull Duration timeToLive) {}
