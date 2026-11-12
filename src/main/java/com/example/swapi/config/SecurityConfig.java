package com.example.swapi.config;

import com.example.swapi.adapter.in.web.ProblemDetailsSecurityHandler;
import com.example.swapi.adapter.in.web.TokenService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

import java.util.List;

/** Stateless security: this service issues and verifies its own HS256 JWTs. No session. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({JwtProperties.class, UserProperties.class})
public class SecurityConfig {

    /** The authority derived from the token's {@code catalog:read} scope. */
    static final String CATALOG_READ = "SCOPE_catalog:read";

    @Bean
    SecurityFilterChain apiFilterChain(HttpSecurity http) throws Exception {
        // 401 and 403 bodies in the same problem+json shape as every other error.
        ProblemDetailsSecurityHandler problems = new ProblemDetailsSecurityHandler();
        return http
                // Tokens travel in a header, not a cookie, so there is nothing to forge.
                // Re-enable CSRF if the app ever moves to cookie auth.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                        // Only mapped under the postgres profile; otherwise a 404.
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/register").permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/actuator/health",
                                "/actuator/health/**",
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs",
                                "/v3/api-docs/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/**").hasAuthority(CATALOG_READ)
                        // Deny by default: a new endpoint stays locked until it is opened here.
                        .anyRequest().denyAll())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint(problems)
                        .accessDeniedHandler(problems))
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(Customizer.withDefaults())
                        .authenticationEntryPoint(problems)
                        .accessDeniedHandler(problems))
                .build();
    }

    /** Pins the algorithm, to rule out algorithm confusion, and validates the issuer and expiry. */
    @Bean
    JwtDecoder jwtDecoder(JwtProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(properties.secretKey())
                .macAlgorithm(MacAlgorithm.HS256)
                .build();

        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.issuer()));
        return decoder;
    }

    /** Signs with the same key and algorithm the decoder accepts. */
    @Bean
    JwtEncoder jwtEncoder(JwtProperties properties) {
        return NimbusJwtEncoder.withSecretKey(properties.secretKey())
                .algorithm(MacAlgorithm.HS256)
                .build();
    }

    /** Built here so the web adapter never imports a config type (enforced by ArchUnit). */
    @Bean
    TokenService tokenService(JwtEncoder jwtEncoder, JwtProperties properties) {
        return new TokenService(jwtEncoder, properties.ttl(), properties.issuer());
    }

    /** Reads the {@code {bcrypt}} prefix, so the hashing scheme can change later. */
    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    /** In-memory users; {@code DbUserDetailsService} replaces this under the postgres profile. */
    @Profile("!postgres")
    @Bean
    UserDetailsService userDetailsService(UserProperties properties) {
        List<UserDetails> users = properties.users().stream()
                .map(user -> (UserDetails) User.withUsername(user.username())
                        // Already a {bcrypt} hash.
                        .password(user.passwordHash())
                        .authorities(List.of())
                        .build())
                .toList();
        return new InMemoryUserDetailsManager(users);
    }

    /** Explicit, so the login path is visible: load the user, compare with BCrypt. */
    @Bean
    AuthenticationManager authenticationManager(UserDetailsService userDetailsService,
                                                PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }
}
