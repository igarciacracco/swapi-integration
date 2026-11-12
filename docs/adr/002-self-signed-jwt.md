# 002 — Self-signed JWT instead of an identity provider

**Status:** Accepted

## Context

The brief requires that catalog endpoints be protected and that a login endpoint issue a token.
It does not require an organisation-wide identity story.

## Decision

The service issues and verifies its own JWTs, signed HS256 with a shared secret held in an
environment variable. Spring Security's OAuth2 resource-server support does the verification, so
there is no hand-written token parsing anywhere.

## Consequences

**Good.** Nothing extra to run or pay for. The app is genuinely stateless, so it survives the
restarts a free host performs without warning, and scales horizontally with no shared session
store. The whole security model is one readable file.

**Bad — and this is the one to say out loud: a token cannot be revoked before it expires.**
There is no server-side state to invalidate, which is precisely why it scales. The mitigation is
a one-hour TTL, which bounds the damage rather than eliminating it. If revocation became a real
requirement, the answer is refresh tokens plus a `jti` deny-list — which brings back exactly the
state this decision removed. That is the trade, stated honestly.

**Also bad.** HS256 means any service that can verify a token can also mint one, because it is
the same key. That is fine while one service does both. The moment a second service needs to
verify, this becomes RS256 so that service gets only the public key.

## Alternatives considered

- **Keycloak or Auth0.** Correct for a real system with several services and real users.
  Rejected here: an entire extra service to run, and a free tier to depend on, for one demo user.
- **Opaque tokens in a store.** Gives real revocation. Rejected because it reintroduces the state
  that makes this app simple, for a benefit the brief does not ask for.
- **HTTP Basic.** Simplest possible. Rejected because the brief asks for a login endpoint
  returning a token, and because it sends the credential on every request.
