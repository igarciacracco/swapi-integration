package com.example.swapi.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

/** JWT settings from the environment. No default secret: a missing or short one fails startup. */
@Validated
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        @NotBlank String secret,
        @NotNull Duration ttl,
        // Must be a URL: Spring's Jwt.getIssuer() parses this claim as java.net.URL.
        @NotBlank @Pattern(regexp = "https?://.+", message = "app.jwt.issuer must be a URL")
        String issuer) {

    /** HS256 needs at least 256 bits of key. */
    private static final int MINIMUM_KEY_BYTES = 32;

    public JwtProperties {
        if (secret != null && keyBytes(secret).length < MINIMUM_KEY_BYTES) {
            throw new IllegalStateException(
                    "app.jwt.secret must decode to at least " + MINIMUM_KEY_BYTES
                            + " bytes for HS256; generate one with: openssl rand -base64 32");
        }
    }

    public SecretKey secretKey() {
        return new SecretKeySpec(keyBytes(secret), "HmacSHA256");
    }

    /** Accepts a base64 secret, falling back to the raw bytes. */
    private static byte[] keyBytes(String secret) {
        try {
            return Base64.getDecoder().decode(secret);
        } catch (IllegalArgumentException notBase64) {
            return secret.getBytes(StandardCharsets.UTF_8);
        }
    }
}
