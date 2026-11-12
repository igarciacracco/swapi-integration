# 001 — Light hexagonal layout, enforced by ArchUnit

**Status:** Accepted

## Context

This service is, at heart, a proxy. The obvious objection to ports and adapters here is that it
is overkill: a controller could call `RestClient` directly and the whole thing would be three
classes.

The complication is that SWAPI is inconsistent *with itself*. Films are not paginated while
people are; films search by `title` while everything else searches by `name`; list responses use
`results` while search and detail responses use `result`. Something has to absorb that, and the
question is only whether it is absorbed in one identified place or smeared across the codebase.

## Decision

A light hexagonal layout: `domain` (plain Java), `application` (one input port, one output port,
one service), `adapter/in/web`, `adapter/out/swapi`, and `config` as the composition root. Source
dependencies point inward.

The cost is two interfaces. Not an interface per class — two.

Crucially, the rule is **enforced**, not documented: ArchUnit tests fail the build if `domain`
depends on Spring or on any adapter, if `application` depends on an adapter, if the web adapter
reaches into the SWAPI adapter, or if a SWAPI DTO escapes the package that owns it.

## Consequences

**Good.** Each SWAPI quirk lives in the adapter that owns it. The core is testable with no HTTP
and no Spring context — the domain and service tests run in about 0.3 seconds combined. Replacing
SWAPI with a database would touch four classes.

**Bad.** There is more indirection than a three-class version. Reading a request end to end means
opening a controller, a port, a service, a port and an adapter.

**Already paid off once.** The layering rule caught `TokenService` — a web adapter — reading a
`config` record. That is the kind of small inversion that normally goes unnoticed until the
dependency graph is a ball of string. It was fixed by having the composition root construct the
service with plain values.

## Alternatives considered

- **Plain layered controller → service → client.** Fewer files. Rejected because the Films quirks
  would end up either in the service or duplicated, and nothing would stop them spreading.
- **Full hexagonal with an interface per use case.** Rejected as ceremony at this size: eight
  single-method interfaces to express what one interface says.
