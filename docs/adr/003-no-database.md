# 003 — No database; a Caffeine cache instead

**Status:** Accepted

## Context

SWAPI is the source of truth and this service performs no writes. The only reasons to add a
database would be latency or availability during an upstream outage.

## Decision

No database. An in-process Caffeine cache in front of `SwapiClient`: bounded at 1,000 entries,
one-hour TTL, keyed by resource plus query.

## Consequences

**Good.** Nothing to provision, migrate, back up or keep in sync. Repeated requests cost nothing
upstream, and cached entries keep serving during a brief SWAPI outage. There is no possibility of
our copy disagreeing with upstream beyond the TTL.

**Bad.** The cache is per instance, so two instances may briefly disagree. For immutable
reference data that is not a real cost; for anything mutable it would mean Redis.

**Bad.** A cold start is slow: every first request pays the full upstream round trip. On a free
host that sleeps, the first user after an idle period pays both the JVM start and a cache miss.

**Bounded on purpose.** `maximumSize` is the difference between a cache and a memory leak, and it
matters on a 512 MB container. The alternative — an unbounded map — is a slow OOM.

## Alternatives considered

- **Postgres mirror of SWAPI, refreshed on a schedule.** Would give fast, outage-proof reads.
  Rejected: it introduces a sync problem and a stale-data problem to solve a performance problem
  the cache already solves, and the brief explicitly does not ask for persistence.
- **Redis.** The right answer at more than one instance. Rejected as premature: another service
  to run for a benefit that only appears after horizontal scaling.
- **No cache at all.** Simplest. Rejected because every request would depend on a third-party API
  with no SLA.

Registration (the optional extra) is the point where a database stops being avoidable, because
user accounts are the first thing this service would actually own.
