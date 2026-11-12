package com.example.swapi.adapter.out.persistence;

import com.example.swapi.application.port.out.UserRepository;
import com.example.swapi.domain.User;
import com.example.swapi.domain.UsernameTakenException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.JdbcClientAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Runs against real Postgres because the behaviour under test is the database's: the
 * case-insensitive unique index and duplicate handling under concurrency.
 *
 * <p>Tagged {@code docker} and excluded from the default build; run with
 * {@code ./mvnw test -Pdocker}.
 */
@Tag("docker")
@Testcontainers
@ActiveProfiles("postgres")
@SpringBootTest(classes = {
        DataSourceAutoConfiguration.class,
        // JdbcClientAutoConfiguration is conditional on a JdbcTemplate, so its own
        // auto-configuration has to be listed too when they are named explicitly.
        JdbcTemplateAutoConfiguration.class,
        JdbcClientAutoConfiguration.class,
        FlywayAutoConfiguration.class,
        JdbcUserRepository.class
})
class JdbcUserRepositoryTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private UserRepository users;

    @Autowired
    private JdbcClient db;

    @BeforeEach
    void clean() {
        db.sql("TRUNCATE TABLE users").update();
    }

    @Test
    @DisplayName("Flyway created the table and a saved user comes back intact")
    void savesAndFindsAUser() {
        User saved = users.save(User.newUser("han_solo", "{bcrypt}$2a$10$hashvalue"));

        assertThat(users.findByUsername("han_solo")).hasValueSatisfying(found -> {
            assertThat(found.id()).isEqualTo(saved.id());
            assertThat(found.username()).isEqualTo("han_solo");
            assertThat(found.passwordHash()).isEqualTo("{bcrypt}$2a$10$hashvalue");
            assertThat(found.createdAt()).isNotNull();
        });
    }

    @Test
    @DisplayName("an unknown username is empty, not an exception")
    void unknownUsernameIsEmpty() {
        assertThat(users.findByUsername("nobody")).isEmpty();
    }

    @Test
    @DisplayName("lookup is case-insensitive, so the stored casing does not matter at login")
    void lookupIgnoresCase() {
        users.save(User.newUser("Han_Solo", "{bcrypt}$2a$10$hashvalue"));

        assertThat(users.findByUsername("han_solo")).isPresent();
        assertThat(users.findByUsername("HAN_SOLO")).isPresent();
        assertThat(users.findByUsername("Han_Solo")).isPresent();
    }

    @Test
    @DisplayName("the unique index rejects a duplicate differing only in case")
    void duplicateDifferingOnlyInCaseIsRejected() {
        users.save(User.newUser("han_solo", "{bcrypt}$2a$10$hashvalue"));

        assertThatExceptionOfType(UsernameTakenException.class)
                .isThrownBy(() -> users.save(User.newUser("HAN_SOLO", "{bcrypt}$2a$10$other")));
    }

    @Test
    @DisplayName("two simultaneous registrations produce exactly one winner")
    void concurrentRegistrationsHaveExactlyOneWinner() throws Exception {
        // Relying on the constraint, not a prior check, is what makes this safe.
        int attempts = 8;
        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();

        try {
            List<? extends Future<?>> futures = java.util.stream.IntStream.range(0, attempts)
                    .mapToObj(i -> pool.submit(() -> {
                        try {
                            users.save(User.newUser("contested", "{bcrypt}$2a$10$hash" + i));
                            succeeded.incrementAndGet();
                        } catch (UsernameTakenException expected) {
                            rejected.incrementAndGet();
                        }
                    }))
                    .toList();
            for (Future<?> future : futures) {
                future.get();
            }
        } finally {
            pool.shutdown();
        }

        assertThat(succeeded).hasValue(1);
        assertThat(rejected).hasValue(attempts - 1);
        assertThat(db.sql("SELECT count(*) FROM users WHERE username = 'contested'")
                .query(Long.class).single()).isEqualTo(1L);
    }
}
