-- The first thing this service actually owns. SWAPI remains the source of truth for the
-- catalog; this table exists because user accounts have no upstream to defer to.

CREATE TABLE users (
    id            UUID         PRIMARY KEY,
    username      VARCHAR(50)  NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Usernames are compared case-insensitively, so uniqueness must be enforced the same way.
-- A plain UNIQUE(username) would happily accept both "Luke" and "luke" and then fail to
-- find one of them at login. The functional index is what makes the rule and the lookup
-- agree, and it is what lets registration rely on the constraint instead of
-- check-then-insert -- which would race two simultaneous signups.
CREATE UNIQUE INDEX users_username_lower_key ON users (LOWER(username));

COMMENT ON COLUMN users.password_hash IS
    'BCrypt hash including its {bcrypt} scheme prefix. Never a plaintext password.';
