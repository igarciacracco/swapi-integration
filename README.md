# Star Wars API

A stateless, JWT-protected REST facade over [swapi.tech](https://www.swapi.tech) for **People**,
**Films**, **Starships** and **Vehicles**. It adds pagination, filtering by `id` and/or `name`,
caching, a consistent error contract and a full test pyramid on top of an upstream API that is
inconsistent between its own endpoints.

Java 21 · Spring Boot 4.1 · Maven · no Lombok · 166 tests (176 with Docker).

---

## Run it in two minutes

You need Docker, or a JDK 21+ (the Maven wrapper fetches Maven itself).

```bash
# 1. Build once. This also downloads Spring's BCrypt, used in step 3.
./mvnw -q -DskipTests package

# 2. A random signing key
export JWT_SECRET="$(openssl rand -base64 32)"

# 3. A BCrypt hash of the demo password, made with jshell (ships with the JDK)
JAR=$(ls ~/.m2/repository/org/springframework/security/spring-security-crypto/*/spring-security-crypto-*.jar | grep -v sources | head -1)
export DEMO_PASSWORD_HASH="$(echo 'System.out.println("{bcrypt}" + org.springframework.security.crypto.bcrypt.BCrypt.hashpw("demo-password-2026", org.springframework.security.crypto.bcrypt.BCrypt.gensalt()))' \
  | jshell -q --class-path "$JAR" | grep -o '{bcrypt}.*')"

# 4. Run
./mvnw spring-boot:run
```

The demo login is `demo` / `demo-password-2026`; change it in step 3 to pick your own password.
With Docker or Apache's `htpasswd` instead of a JDK, the hash comes from:

```bash
docker run --rm httpd:2.4-alpine htpasswd -nbBC 10 "" 'demo-password-2026' | tr -d ':\n' | sed 's/^/{bcrypt}/'
```

> **Quote the hash in single quotes.** A BCrypt hash contains `$2a$10$`, and an unquoted
> `source .env` will let the shell expand `$2` and `$10` into positional parameters, silently
> corrupting it. The symptom is a 401 on correct credentials. This bit me while testing, so it
> is written down rather than rediscovered.

Or with Docker:

```bash
docker build -t swapi-integration .
docker run -p 8080:8080 \
  -e JWT_SECRET="$(openssl rand -base64 32)" \
  -e DEMO_PASSWORD_HASH='{bcrypt}$2a$10$.7J2clSyyeZWzuwz/N/8hOkhVu5c4eWsIi8AEGRs6DpffTaXIbbOK' \
  swapi-integration
```

Then open **<http://localhost:8080/swagger-ui.html>**, click **Authorize**, and paste a token.

---

## The whole API in one copy-paste

```bash
# Log in. Everything else needs this token.
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"demo","password":"demo-password-2026"}' | jq -r .accessToken)

# A page of people. SWAPI paginates this upstream, so the totals are real.
curl -s -H "Authorization: Bearer $TOKEN" \
  'http://localhost:8080/api/v1/people?size=3' | jq

# The second page of films. SWAPI does NOT paginate films -- it returns all six
# however you ask -- so this page was computed here. The envelope is identical.
curl -s -H "Authorization: Bearer $TOKEN" \
  'http://localhost:8080/api/v1/films?page=2&size=2' | jq

# Filter by name: case-insensitive "contains", matching SWAPI's own semantics.
curl -s -H "Authorization: Bearer $TOKEN" \
  'http://localhost:8080/api/v1/people?name=sky' | jq

# Filter by id and name together. Matching -> one result.
curl -s -H "Authorization: Bearer $TOKEN" \
  'http://localhost:8080/api/v1/people?id=1&name=luke' | jq

# Not matching -> 200 with an empty page, NOT a 404. See "Two kinds of nothing" below.
curl -s -H "Authorization: Bearer $TOKEN" \
  'http://localhost:8080/api/v1/people?id=1&name=vader' | jq

# A detail lookup, where an unknown id IS a 404.
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/people/1 | jq
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/people/9999 | jq

# Validation and auth failures, both as RFC 9457 problem+json.
curl -s -H "Authorization: Bearer $TOKEN" \
  'http://localhost:8080/api/v1/people?size=500' | jq
curl -s -i http://localhost:8080/api/v1/people | head -1          # 401
```

There is no hosted instance: run it locally as shown above. See [Deploying](#deploying) to host
your own.

---

## Endpoints

| Method | Path | Auth | Purpose |
| --- | --- | --- | --- |
| `POST` | `/api/v1/auth/login` | public | Exchange username + password for a JWT |
| `GET` | `/api/v1/{people,films,starships,vehicles}` | `catalog:read` | Paginated list, optional `id` / `name` filters |
| `GET` | `/api/v1/{people,films,starships,vehicles}/{id}` | `catalog:read` | The full record |
| `GET` | `/actuator/health` | public | Liveness, for the host |
| `GET` | `/swagger-ui.html`, `/v3/api-docs` | public | Interactive docs |

**List parameters**

| Param | Type | Default | Rule |
| --- | --- | --- | --- |
| `page` | int | 1 | 1-based, ≥ 1 |
| `size` | int | 10 | 1–100 |
| `id` | digits | — | Exact match; anything else is a 400 |
| `name` | string | — | Case-insensitive contains, ≤ 100 chars |

**List response**

```json
{
  "items": [{ "id": "1", "name": "Luke Skywalker" }],
  "page": 1, "size": 10, "totalItems": 82, "totalPages": 9
}
```

**Errors** are always `application/problem+json` (RFC 9457):

| Status | When |
| --- | --- |
| 400 | Bad `page`/`size`/`name`, or a malformed login body |
| 401 | Missing, invalid or expired token; wrong credentials |
| 403 | Valid token without the `catalog:read` scope |
| 404 | Unknown path, or unknown id on a **detail** endpoint |
| 502 | SWAPI returned an error |
| 504 | SWAPI did not answer in time |

A 502 or 504 body carries a `correlationId`. The upstream status and cause are in the server log
under that id and deliberately never in the response.

---

## Architecture

Light hexagonal. **Source dependencies point inward**, and an ArchUnit test fails the build if
they ever stop doing so.

```
          ┌─────────────── adapter/in/web ───────────────┐
          │  People/Films/Starships/VehiclesController   │
          │  AuthController · ApiExceptionHandler        │
          └──────────────────────┬──────────────────────-┘
                                 │  depends on
                      ┌──────────▼───────────┐
                      │  StarWarsQueries     │   input port
                      ├──────────────────────┤
                      │ StarWarsQueryService │   the id/name filter rules, once
                      ├──────────────────────┤
                      │  EntityCatalog<T>    │   output port (owned by the core)
                      └──────────▲───────────┘
                                 │  implemented by
      ┌──────────────────────────┴───────────────────────────┐
      │ SwapiPeopleAdapter  SwapiFilmsAdapter                │
      │ SwapiStarshipsAdapter  SwapiVehiclesAdapter          │  adapter/out/swapi
      │              all sharing one SwapiClient             │
      └──────────────────────────────────────────────────────┘

      domain/   pure Java, imports nothing but java.*
      config/   the composition root: the only package allowed to see every layer
```

The arrow from the adapters up to `EntityCatalog<T>` is the dependency inversion. At runtime the
service calls an adapter; at compile time each adapter depends on an interface the core owns.
Replacing SWAPI with a database means four new classes and nothing else.

### Decisions worth defending

**One input port, one generic output port.** `EntityCatalog<T extends StarWarsResource>` is
everything the core needs from a data source. Each entity gets its own implementation, so each
entity's upstream quirks live in the adapter that owns them. The service constructor asks for
`EntityCatalog<Person>`, `EntityCatalog<Film>`, … and Spring matches each one by its generic type
argument — no `@Qualifier`, no named sub-interfaces.

**Films is the reason this pays for itself.** SWAPI is inconsistent with itself:

| | people / starships / vehicles | films |
| --- | --- | --- |
| List | paginated: `total_records`, `total_pages`, `results` | **not paginated**: all six, under `result` |
| Search | `?name=` | **`?title=`** |
| Name field | `name` | **`title`** |

All of that stops inside `SwapiFilmsAdapter`. There is no `if (films)` anywhere in the codebase,
and `/api/v1/films?page=2&size=2` returns the same envelope as a genuinely paginated resource.

**Four controllers, not one `/{resource}`.** The URLs come out identical, so the argument is
everything else: each endpoint gets a typed OpenAPI schema (`/people/{id}` really does document
`PersonDto`), there is no string-to-type conversion to write or test, and Spring 404s unknown
paths for free.

**Two kinds of nothing.** A filter matching nothing is `200` with an empty page; an unknown id on
a detail endpoint is `404`. "I searched and found nothing" and "this thing does not exist" are
different answers and deserve different status codes.

**Numbers that aren't numbers.** `height` and `mass` stay strings, because SWAPI returns
`"unknown"` and `"1,358"`. Parsing invents data; failing hides real records.

---

## Security

The service issues and verifies its own short-lived JWTs — the standard self-signed JWT setup.
No session, no token store, no identity provider to operate.

1. `POST /api/v1/auth/login` → `AuthenticationManager` → `DaoAuthenticationProvider` → BCrypt.
2. `TokenService` signs claims (`iss`, `sub`, `iat`, `exp`, `scope: catalog:read`) with HS256.
3. The client sends `Authorization: Bearer <jwt>`; the resource-server filter verifies the
   signature, the expiry and the issuer, and maps `scope` to the authority `SCOPE_catalog:read`.
   No controller ever touches a token.

| Choice | Why | Trade-off accepted |
| --- | --- | --- |
| Stateless JWT | No session store, scales horizontally, survives a restart | **A token cannot be revoked before it expires.** Mitigated by a 1h TTL |
| HS256 | One service both signs and verifies; one secret | If another service needed to verify, switch to RS256 so it only gets the public key |
| Scope-based authorization | Separates authN from authZ; an admin scope is one more line | Only one scope today |
| Deny by default | A new endpoint is locked until someone opens it deliberately | — |
| CSRF disabled | The token is in a header the browser does not attach automatically | Must come back if this ever moves to cookies |

Beyond the brief, each with a test: the decoder pins the MAC algorithm rather than trusting the
token's own header; the issuer is validated, which Spring does not do by default; the secret has
no default and is rejected under 32 bytes; and a wrong password and an unknown username return
**byte-identical** 401 bodies, so usernames cannot be enumerated.

Not built, but the next things I would do: rate-limit `/auth/login` (Bucket4j), refresh tokens
with a `jti` deny-list if revocation became a requirement, and key rotation via a `kid` header.

---

## Testing

166 tests, plus 10 that need Docker. The suite runs in well under a minute and never calls the
real SWAPI.

| Layer | What it proves | Count |
| --- | --- | --- |
| Domain unit | Pagination arithmetic and invariants | 33 |
| Service unit | The id/name filter table, via one generic fake catalog | 15 |
| Registration unit | Username and password rules, hashing, duplicates, demo-user seeding | 26 |
| SWAPI adapters | Each adapter's mapping and quirks, against saved fixtures | 26 |
| Web slice | HTTP contract, validation, error mapping | 25 |
| Security | 401 vs 403, deny-by-default, token contents | 20 |
| Cache | That `@Cacheable` is actually on the call path | 4 |
| Integration | The whole app against WireMock, including real login | 9 |
| Architecture | The dependency rules, enforced in CI | 8 |
| Testcontainers | Real Postgres: the unique index, and a concurrent-signup race | 10 |

Some notes on how, not just what:

- **A fake, not a mock, for the port.** One generic `FakeEntityCatalog<T>` serves all four
  entities. It behaves like the real thing, so tests read as scenarios and survive refactoring,
  and it counts calls — which is how the id-filter test proves it does one lookup rather than
  listing everything and filtering.
- **Fixtures are real.** `src/test/resources/swapi/` holds ten unedited responses captured with
  curl, so the adapter tests double as a record of the contract. They can go stale silently,
  which is what the live test is for.
- **The cache test counts HTTP requests, not return values.** `@Cacheable` works through a proxy,
  so a class calling its own cached method skips the cache and *nothing warns you* — every test
  still passes and answers are still correct. Counting requests is the only honest check.
- **The architecture rules were verified by breaking them.** Worth knowing: ArchUnit reads
  bytecode, so an unused `import org.springframework...` in the domain does **not** trip the rule
  — but any actual use does, and fails the build naming the exact method.

```bash
./mvnw verify                 # 158 tests: everything that needs neither Docker nor the internet
./mvnw test -Pdocker          # +10 Testcontainers tests for registration (needs a daemon)
./mvnw test -Plive            # the one test that calls the real SWAPI
```

`verify` deliberately excludes both, so the default build runs on a laptop with no Docker and no
network. CI has both, and runs all three.

The `live` test asserts on the *contract* (people are paginated, films are not, films search by
title) rather than on the data, so it does not fail the day someone adds a character.

---

## Configuration

| Variable | Required | Purpose |
| --- | --- | --- |
| `JWT_SECRET` | **yes** | Base64, ≥ 32 bytes. No default: a default would be identical on every deployment that forgot to set one |
| `DEMO_PASSWORD_HASH` | **yes** | BCrypt hash with its `{bcrypt}` prefix. Single-quote it |
| `DEMO_USERNAME` | no | Defaults to `demo` |
| `PORT` | no | Defaults to 8080; the host injects this |

Everything else (`swapi.base-url`, timeouts, cache size and TTL) is in `application.yml` and
typed through validated `@ConfigurationProperties` records, so a bad value fails at startup
rather than on the first request. See `.env.example`.

---

## Deploying

The Dockerfile is multi-stage: it builds on a real Temurin **21** JDK and ships a 355 MB
runtime image on `eclipse-temurin:21-jre-alpine`, running as a non-root user, with a Docker
`HEALTHCHECK` wired to `/actuator/health`. Alpine over the Ubuntu-based tag because it is
roughly half the size (525 MB → 355 MB), which shortens the image pull on a host that
cold-starts often.

Render's free web service is the recommended host. The challenge brief's suggestions are out of
date: Heroku has had no free tier since November 2022, and Vercel runs serverless functions
rather than a long-lived JVM.

The repo includes a [`render.yaml`](render.yaml) Blueprint, so setup is: **New → Blueprint →**
pick this repo **→ Apply**. It creates a free Docker web service with the health check on
`/actuator/health`, generates a random `JWT_SECRET`, sets the demo `DEMO_PASSWORD_HASH`, and only
deploys a push to `main` after CI passes.

> **Expect the first request after an idle period to take ~30 seconds.** Render's free tier
> sleeps after 15 minutes and a JVM cold start on 0.1 CPU is slow. Spring AOT or a GraalVM
> native image is the real fix; it was out of scope here.

CI runs `./mvnw -B verify` on Temurin 21 for every push and PR — unit, slice, integration,
architecture tests and JaCoCo.

### A note on Java versions

The build targets **Java 21** (`<java.version>21</java.version>`, so `javac` compiles with
`--release 21`: verified class-file major version 65). CI and the Docker image both use a real
Temurin 21. Local development happens to be on JDK 25, which is fine precisely because
`--release 21` compiles against the Java 21 API and nothing newer can leak in.

---

## Registration (the optional extra)

The core API has no database, because it owns no data — SWAPI is the source of truth. Accounts
are the exception: a user has no upstream to defer to. So registration lives behind a profile,
and the deployed demo runs without a database at all.

```bash
docker compose up -d                                        # local Postgres
JWT_SECRET=... DEMO_PASSWORD_HASH='{bcrypt}...'   ./mvnw spring-boot:run -Dspring-boot.run.profiles=postgres

curl -s -X POST http://localhost:8080/api/v1/auth/register   -H 'Content-Type: application/json'   -d '{"username":"han_solo","password":"correct-horse-battery-staple"}'
# 201 {"username":"han_solo"}     then log in normally
```

| Piece | Choice |
| --- | --- |
| Schema | Flyway `V1__users.sql`, with a **functional** unique index on `LOWER(username)` |
| Repository | `JdbcClient` and plain SQL — one table, two queries, no JPA |
| Duplicates | Caught from the unique-constraint violation, never check-then-insert |
| Login | `DbUserDetailsService` replaces the in-memory one. Login, tokens and the filter chain are untouched |
| Demo user | An `ApplicationRunner` seeds it from the env hash if missing — never in a migration, so it stays out of Git |

Three decisions with a real argument behind them:

- **The unique index is on `LOWER(username)`, not on `username`.** A plain `UNIQUE` would accept
  both `Luke` and `luke` and then fail to find one of them at login. The functional index makes
  the uniqueness rule and the lookup agree.
- **Duplicates come from the constraint, not a prior check.** Check-then-insert lets two
  simultaneous signups both pass the check. There is a Testcontainers test that fires eight
  concurrent registrations for the same name and asserts exactly one winner.
- **Passwords over 72 bytes are rejected, not truncated.** BCrypt silently ignores anything past
  72 bytes, so a long passphrase would be accepted, quietly weakened, and any other string
  sharing its first 72 bytes would also unlock the account. The limit is counted in bytes, which
  a test pins with emoji.

Known trade-off: a 409 confirms that a username exists, so the endpoint can be used to enumerate
accounts. The stricter alternative answers identically either way and confirms by email, which
needs a channel this service doesn't have. Recorded rather than accidental.

> Note: the deployed demo does **not** run this profile. Render's free disk is wiped on restart
> and its free Postgres expires after 30 days, so `docker compose` is the intended way to try it.

## Explicitly out of scope

- Password reset, refresh tokens, token revocation
- Persisting SWAPI data, writes of any kind, a frontend
- Rate limiting, a circuit breaker, retries with backoff — timeouts plus a clear 502/504 were
  judged enough for this brief; the next steps are named above

## What I would do next

1. **Rate-limit the login endpoint.** It is the one unauthenticated, computation-heavy endpoint
   (BCrypt is deliberately slow), which makes it the obvious target.
2. **Retry with backoff on idempotent GETs**, then a circuit breaker if SWAPI proved flaky.
   Everything is a GET, so retries are safe.
3. **Move the cache to Redis** if this ran as more than one instance — today each instance keeps
   its own, which is fine only because the data is immutable.
4. **Spring AOT / native image** to kill the cold start.
5. **Package-by-feature** if the entity set grew. Package-by-layer is readable at four entities;
   it stops being readable at twenty.
