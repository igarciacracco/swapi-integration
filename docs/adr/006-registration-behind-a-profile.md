# 006 — Registration behind a profile, not in the core

**Status:** Accepted

## Context

ADR 003 says this service has no database, because it owns no data. Registration breaks that
premise: a user account has no upstream to defer to, so somebody has to store it. The question
is what that does to the rest of the application.

The constraint that shapes the answer is the deployment. Render's free disk is wiped on restart
and its free Postgres expires after 30 days, so the deployed demo cannot depend on a database
and still be a reliable demo.

## Decision

Registration is a Spring profile. Without `postgres`, the datasource and Flyway
auto-configurations are excluded, an in-memory `UserDetailsService` serves the demo user, and
`/api/v1/auth/register` does not exist. With it, Flyway migrates, `JdbcUserRepository` is active,
`DbUserDetailsService` replaces the in-memory store, and the endpoint appears.

## Consequences

**Good.** The deployed demo is a stateless app with no database to lose, exactly as before. The
catalog code is untouched — the full-context test proves a newly registered account can log in
and read the catalog without any catalog class knowing a database exists.

**Good.** It makes the claim in ADR 002 falsifiable rather than rhetorical. Swapping the user
store really is one adapter class, and there is a test that exercises both stores through the
same login endpoint.

**Bad.** Two configurations means two things to keep working, and the profile that CI exercises
with Testcontainers is not the one that gets deployed. A reader has to know which mode they are
in. The README says so in both places.

**Bad.** `spring.autoconfigure.exclude` in the default profile is a blunt instrument. It works
because the profile-specific file overrides the list, but it is the kind of configuration that
is obvious once seen and baffling until then, so it is commented in both files.

## What it caught

Adding the endpoint was not enough to make it reachable: `anyRequest().denyAll()` kept returning
401 until a `permitAll` line was added for it. That is deny-by-default doing precisely its job —
a new endpoint stayed closed until someone opened it on purpose — and it is worth noting that a
test found it rather than a user.

## Alternatives considered

- **Always require a database.** Simplest configuration, one code path. Rejected: it would make
  the deployed demo depend on a free Postgres that expires, to support a feature the brief lists
  as optional.
- **Condition on `spring.datasource.url` being present** rather than on a profile. Rejected as
  less explicit: `--spring.profiles.active=postgres` states the intent, whereas inferring the
  mode from whether a URL happens to be set makes "why is registration missing?" a debugging
  session.
- **An embedded H2 database.** Would let registration run everywhere. Rejected because the
  behaviour worth testing is Postgres-specific — a functional unique index on `LOWER(username)`
  and the duplicate-key error it raises — and H2's compatibility mode would be a guess at it.
