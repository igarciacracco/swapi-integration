package com.example.swapi.application;

import com.example.swapi.application.port.in.RegisterUser;
import com.example.swapi.application.port.out.UserRepository;
import com.example.swapi.domain.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/** Creates accounts. The raw password is only hashed: never logged, returned or stored. */
@Profile("postgres")
@Service
public class RegistrationService implements RegisterUser {

    private static final Logger log = LoggerFactory.getLogger(RegistrationService.class);

    private static final Pattern USERNAME = Pattern.compile("^[A-Za-z0-9_.-]{3,50}$");
    private static final int MIN_PASSWORD_LENGTH = 12;

    /** BCrypt ignores input past 72 bytes, so longer passwords are rejected, not truncated. */
    private static final int MAX_PASSWORD_BYTES = 72;

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    RegistrationService(UserRepository users, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public String register(String username, String rawPassword) {
        validateUsername(username);
        validatePassword(rawPassword);

        User saved = users.save(User.newUser(username, passwordEncoder.encode(rawPassword)));

        log.info("Registered user '{}'", saved.username());
        return saved.username();
    }

    private static void validateUsername(String username) {
        if (username == null || !USERNAME.matcher(username).matches()) {
            throw new IllegalArgumentException(
                    "username must be 3-50 characters of letters, digits, underscore, dot or hyphen");
        }
    }

    /** Length only, no composition rules. */
    private static void validatePassword(String rawPassword) {
        if (rawPassword == null || rawPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException(
                    "password must be at least " + MIN_PASSWORD_LENGTH + " characters");
        }
        if (rawPassword.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            throw new IllegalArgumentException(
                    "password must be at most " + MAX_PASSWORD_BYTES + " bytes; "
                            + "BCrypt ignores anything beyond that, so it is rejected rather "
                            + "than silently truncated");
        }
    }
}
