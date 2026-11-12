# 004 — Paginate Films and name searches in memory

**Status:** Accepted

## Context

Verified against swapi.tech on 2026-11-12:

- `GET /films` returns all six films in one response and **ignores `page` and `limit`**.
- `GET /people/?name=sky` returns every match in one response, also unpaginated.
- `GET /people?page=1&limit=10` *is* paginated, with `total_records` and `total_pages`.

So the API must offer a uniform paginated contract over an upstream that paginates only
sometimes.

## Decision

`PageResult.slice(...)` in the domain performs the arithmetic, and the adapter that faces an
unpaginated endpoint calls it. `SwapiFilmsAdapter.list` fetches all six films and slices;
every name search slices the match list. Endpoints SWAPI does paginate pass straight through and
forward the real upstream totals.

The pagination maths lives in the domain, not an adapter, because it is a rule of this API rather
than of SWAPI — and because that makes it the most heavily unit-tested method in the project.

## Consequences

**Good.** The client sees one contract. `/films?page=2&size=2` behaves exactly like
`/people?page=2&size=2`, including `totalItems` and `totalPages`. The service layer never learns
which upstream endpoints are paginated.

**Bad, and the obvious thing to probe in review: this does not scale.** It is safe here only
because the collections are tiny and fixed — six films, a handful of search matches. Fetching
everything to return two items would be indefensible at a thousand rows.

**The threshold where it stops being acceptable** is roughly when a full fetch no longer fits
comfortably in memory or in the latency budget. At that point the options are, in order: push
pagination upstream if the API grows support for it; index the data locally and paginate the
index; or cap the result set and tell the client it was truncated. The current design makes that
change local to one adapter.

## Alternatives considered

- **Expose SWAPI's inconsistency to the client** — paginate people, do not paginate films.
  Rejected: it pushes the problem onto every consumer and makes the OpenAPI document lie.
- **Paginate in the service with an `if (films)` branch.** Rejected: that is precisely the
  coupling the adapter layer exists to prevent, and it would grow a branch per quirk.
