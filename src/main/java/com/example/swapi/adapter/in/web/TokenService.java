package com.example.swapi.adapter.in.web;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

import java.time.Duration;
import java.time.Instant;

/** Issues the access tokens this service verifies. Claims are minimal: a JWT is not encrypted. */
public class TokenService {

    /** Spring Security maps each space-separated value to a {@code SCOPE_} authority. */
    static final String SCOPE_CLAIM = "scope";
    static final String CATALOG_READ_SCOPE = "catalog:read";

    private final JwtEncoder encoder;
    private final Duration ttl;
    private final String issuer;

    /** Takes plain values, not {@code JwtProperties}, so this adapter does not depend on config. */
    public TokenService(JwtEncoder encoder, Duration ttl, String issuer) {
        this.encoder = encoder;
        this.ttl = ttl;
        this.issuer = issuer;
    }

    public IssuedToken issueFor(String username) {
        Instant now = Instant.now();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(username)
                .issuedAt(now)
                .expiresAt(now.plus(ttl))
                .claim(SCOPE_CLAIM, CATALOG_READ_SCOPE)
                .build();

        // Explicit header: with a symmetric key the encoder cannot infer the algorithm.
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();

        String value = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedToken(value, ttl.toSeconds());
    }

    /** There is no revocation; the short TTL bounds exposure. */
    public record IssuedToken(String value, long expiresInSeconds) {}
}
