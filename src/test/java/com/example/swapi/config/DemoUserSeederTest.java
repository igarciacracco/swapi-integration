package com.example.swapi.config;

import com.example.swapi.application.port.out.UserRepository;
import com.example.swapi.domain.User;
import com.example.swapi.domain.UsernameTakenException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class DemoUserSeederTest {

    private static final UserProperties DEMO = new UserProperties(
            List.of(new UserProperties.User("demo", "{bcrypt}$2a$10$hash")));

    @Test
    @DisplayName("a missing demo user is stored with the configured hash, unchanged")
    void seedsMissingUser() {
        RecordingRepository users = new RecordingRepository(false, false);

        new DemoUserSeeder(users, DEMO).run(null);

        assertThat(users.saved).singleElement().satisfies(user -> {
            assertThat(user.username()).isEqualTo("demo");
            assertThat(user.passwordHash()).isEqualTo("{bcrypt}$2a$10$hash");
        });
    }

    @Test
    @DisplayName("an existing demo user is left alone")
    void skipsExistingUser() {
        RecordingRepository users = new RecordingRepository(true, false);

        new DemoUserSeeder(users, DEMO).run(null);

        assertThat(users.saved).isEmpty();
    }

    @Test
    @DisplayName("losing the insert race to another instance does not fail startup")
    void toleratesConcurrentSeeding() {
        RecordingRepository users = new RecordingRepository(false, true);

        assertThatCode(() -> new DemoUserSeeder(users, DEMO).run(null))
                .doesNotThrowAnyException();
    }

    /** Simulates "absent at check time" and, optionally, "taken at insert time". */
    private static final class RecordingRepository implements UserRepository {

        private final boolean exists;
        private final boolean takenOnSave;
        private final List<User> saved = new ArrayList<>();

        RecordingRepository(boolean exists, boolean takenOnSave) {
            this.exists = exists;
            this.takenOnSave = takenOnSave;
        }

        @Override
        public Optional<User> findByUsername(String username) {
            return exists ? Optional.of(User.newUser(username, "x")) : Optional.empty();
        }

        @Override
        public User save(User user) {
            if (takenOnSave) {
                throw new UsernameTakenException(user.username());
            }
            saved.add(user);
            return user;
        }
    }
}
