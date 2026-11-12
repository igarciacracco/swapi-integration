package com.example.swapi.adapter.out.persistence;

import com.example.swapi.application.port.out.UserRepository;
import com.example.swapi.domain.User;
import com.example.swapi.domain.UsernameTakenException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

/** Plain SQL through {@code JdbcClient}; one table does not justify JPA. */
@Profile("postgres")
@Repository
class JdbcUserRepository implements UserRepository {

    private final JdbcClient db;

    JdbcUserRepository(JdbcClient db) {
        this.db = db;
    }

    @Override
    public Optional<User> findByUsername(String username) {
        // LOWER on both sides matches the functional unique index.
        return db.sql("""
                        SELECT id, username, password_hash, created_at
                        FROM users
                        WHERE LOWER(username) = LOWER(:username)
                        """)
                .param("username", username)
                .query(JdbcUserRepository::toUser)
                .optional();
    }

    @Override
    public User save(User user) {
        try {
            db.sql("""
                            INSERT INTO users (id, username, password_hash, created_at)
                            VALUES (:id, :username, :passwordHash, :createdAt)
                            """)
                    .param("id", user.id())
                    .param("username", user.username())
                    .param("passwordHash", user.passwordHash())
                    .param("createdAt", java.sql.Timestamp.from(user.createdAt()))
                    .update();
            return user;
        } catch (DuplicateKeyException e) {
            // The constraint decides, not a prior check, so concurrent sign-ups can't both succeed.
            throw new UsernameTakenException(user.username());
        }
    }

    private static User toUser(ResultSet rs, int rowNum) throws SQLException {
        return new User(
                rs.getObject("id", java.util.UUID.class),
                rs.getString("username"),
                rs.getString("password_hash"),
                rs.getTimestamp("created_at").toInstant());
    }
}
