package com.example.swapi.application;

import com.example.swapi.application.port.out.UserRepository;
import com.example.swapi.domain.User;
import com.example.swapi.domain.UsernameTakenException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * Registration rules with a fake repository and a real BCrypt encoder, so the tests prove the
 * stored value is a hash that verifies.
 */
class RegistrationServiceTest {

    private static final String VALID_PASSWORD = "correct-horse-battery-staple";

    private FakeUserRepository users;
    private PasswordEncoder encoder;
    private RegistrationService service;

    @BeforeEach
    void setUp() {
        users = new FakeUserRepository();
        encoder = new BCryptPasswordEncoder();
        service = new RegistrationService(users, encoder);
    }

    @Test
    @DisplayName("what is stored is a hash that verifies, never the password")
    void storesAVerifiableHash() {
        service.register("han_solo", VALID_PASSWORD);

        User stored = users.findByUsername("han_solo").orElseThrow();
        assertThat(stored.passwordHash()).isNotEqualTo(VALID_PASSWORD);
        assertThat(stored.passwordHash()).startsWith("$2");
        assertThat(encoder.matches(VALID_PASSWORD, stored.passwordHash())).isTrue();
        assertThat(encoder.matches("something-else-entirely", stored.passwordHash())).isFalse();
    }

    @Test
    @DisplayName("toString does not leak the hash into logs")
    void toStringHidesTheHash() {
        service.register("han_solo", VALID_PASSWORD);

        User stored = users.findByUsername("han_solo").orElseThrow();
        assertThat(stored.toString()).contains("han_solo").doesNotContain(stored.passwordHash());
    }

    @Test
    @DisplayName("a duplicate username is a conflict, surfaced from the repository")
    void duplicateUsernameIsRejected() {
        service.register("han_solo", VALID_PASSWORD);

        assertThatExceptionOfType(UsernameTakenException.class)
                .isThrownBy(() -> service.register("han_solo", VALID_PASSWORD))
                .satisfies(e -> assertThat(e.username()).isEqualTo("han_solo"));
    }

    @Test
    @DisplayName("the duplicate check is case-insensitive, matching the database index")
    void duplicateDetectionIgnoresCase() {
        service.register("han_solo", VALID_PASSWORD);

        assertThatExceptionOfType(UsernameTakenException.class)
                .isThrownBy(() -> service.register("HAN_SOLO", VALID_PASSWORD));
    }

    @ParameterizedTest(name = "username [{0}] is rejected")
    @NullSource
    @ValueSource(strings = {
            "ab",                   // too short
            "has space",
            "has@symbol",
            "emoji😀"
    })
    void invalidUsernamesAreRejected(String username) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.register(username, VALID_PASSWORD));
    }

    @Test
    @DisplayName("51 characters is one too many; 50 is accepted")
    void usernameLengthBoundary() {
        assertThat(service.register("a".repeat(50), VALID_PASSWORD)).hasSize(50);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.register("b".repeat(51), VALID_PASSWORD));
    }

    @ParameterizedTest(name = "username [{0}] is accepted")
    @ValueSource(strings = {"abc", "han_solo", "r2.d2", "obi-wan", "Leia2"})
    void validUsernamesAreAccepted(String username) {
        assertThat(service.register(username, VALID_PASSWORD)).isEqualTo(username);
    }

    @ParameterizedTest(name = "a {0}-character password is rejected as too short")
    @ValueSource(ints = {0, 1, 11})
    void shortPasswordsAreRejected(int length) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.register("han_solo", "x".repeat(length)));
    }

    @Test
    @DisplayName("12 characters is accepted: the boundary is inclusive")
    void twelveCharactersIsEnough() {
        assertThat(service.register("han_solo", "x".repeat(12))).isEqualTo("han_solo");
    }

    @Test
    @DisplayName("73 bytes is rejected rather than silently truncated by BCrypt")
    void overlyLongPasswordsAreRejectedNotTruncated() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.register("han_solo", "x".repeat(73)))
                .withMessageContaining("72 bytes");
    }

    @Test
    @DisplayName("72 bytes is accepted: the boundary is inclusive")
    void seventyTwoBytesIsAccepted() {
        assertThat(service.register("han_solo", "x".repeat(72))).isEqualTo("han_solo");
    }

    @Test
    @DisplayName("the limit is counted in bytes, not characters: one emoji is four bytes")
    void theLimitIsInBytesNotCharacters() {
        // 18 four-byte emoji = 72 bytes, so this is at the limit despite being 36 chars long.
        String atLimit = "😀".repeat(18);
        assertThat(atLimit.getBytes(java.nio.charset.StandardCharsets.UTF_8)).hasSize(72);
        assertThat(service.register("emoji_user", atLimit)).isEqualTo("emoji_user");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.register("other_user", atLimit + "😀"));
    }

    @Test
    @DisplayName("a rejected registration never reaches the repository")
    void invalidInputDoesNotTouchTheRepository() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.register("ab", "short"));

        assertThat(users.saved).isEmpty();
    }

    /** Enforces the same case-insensitive uniqueness as the database index. */
    private static final class FakeUserRepository implements UserRepository {

        private final List<User> saved = new ArrayList<>();

        @Override
        public Optional<User> findByUsername(String username) {
            return saved.stream()
                    .filter(u -> u.username().toLowerCase(Locale.ROOT)
                            .equals(username.toLowerCase(Locale.ROOT)))
                    .findFirst();
        }

        @Override
        public User save(User user) {
            if (findByUsername(user.username()).isPresent()) {
                throw new UsernameTakenException(user.username());
            }
            saved.add(user);
            return user;
        }
    }
}
