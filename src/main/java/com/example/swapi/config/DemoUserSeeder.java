package com.example.swapi.config;

import com.example.swapi.application.port.out.UserRepository;
import com.example.swapi.domain.User;
import com.example.swapi.domain.UsernameTakenException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Creates the configured demo user on startup if missing. The value is already a BCrypt hash, so
 * it is stored as-is, and it comes from the environment so no credential is committed.
 *
 * <p>The existence check only avoids a pointless insert. When two instances start together, both
 * can pass it; the unique constraint then rejects one insert, and that instance carries on.
 */
@Profile("postgres")
@Component
class DemoUserSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoUserSeeder.class);

    private final UserRepository users;
    private final UserProperties properties;

    DemoUserSeeder(UserRepository users, UserProperties properties) {
        this.users = users;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (UserProperties.User configured : properties.users()) {
            if (users.findByUsername(configured.username()).isPresent()) {
                log.debug("Seed user '{}' already exists", configured.username());
                continue;
            }
            try {
                users.save(User.newUser(configured.username(), configured.passwordHash()));
                log.info("Seeded user '{}' from configuration", configured.username());
            } catch (UsernameTakenException alreadySeeded) {
                log.info("Seed user '{}' was created concurrently", configured.username());
            }
        }
    }
}
