# 005 — One generic output port, one adapter per entity

**Status:** Accepted

## Context

Four entities, served by an upstream that treats one of them (films) differently from the other
three. The question is how many interfaces and how many implementations.

## Decision

One generic interface, `EntityCatalog<T extends StarWarsResource>`, with three methods: `list`,
`searchByName`, `findById`. Four implementations, one per entity. All four share a single
`SwapiClient` **by composition**, not by extending a base class.

`StarWarsQueryService` asks for `EntityCatalog<Person>`, `EntityCatalog<Film>`,
`EntityCatalog<Starship>` and `EntityCatalog<Vehicle>` as constructor parameters, and Spring
resolves each to the one bean implementing that parameterised type.

## Consequences

**Good.** Each adapter owns its entity's quirks, so `SwapiFilmsAdapter` can paginate in memory
and search by `title` without anything else knowing. Each adapter is about twenty lines. No
`@Qualifier` is needed anywhere.

**Good.** Sealing `StarWarsResource` closes the set of types that can be plugged in, and its
`id()`/`name()` accessors are what let the service's single generic filter method work for any
entity — which is why the id/name rules exist once rather than four times.

**Objection: isn't that four copies of the same class?** Three of them are nearly identical, yes.
But everything that would actually be duplicated — HTTP, JSON, timeouts, error translation,
caching — lives once in `SwapiClient`. What remains per adapter is the resource name and the
entity type, which is the part that genuinely differs. A base class would hide that in
inheritance; composition leaves it visible in a constructor.

**Bad.** Adding a fifth entity edits `StarWarsQueries`, because the input port lists methods per
entity. That is acceptable for SWAPI's fixed set. If the set grew, the move is one input port per
entity.

**Verified, not assumed.** The generic injection is exactly the sort of thing that works in
theory and fails at runtime through type erasure, so the integration test exercises all four
entities through the real context, and a service unit test gives a Person and a Vehicle the same
id so a crossed wire would fail loudly.

## Alternatives considered

- **One adapter with a `switch` on an entity type.** Rejected: every quirk becomes a branch, and
  the class grows with the entity set.
- **Four unrelated interfaces (`PeopleCatalog`, `FilmCatalog`, …).** Rejected: four identical
  interfaces is ceremony, and the service could no longer share one generic filter method.
- **A generic base adapter class with overridable hooks.** Rejected: inheritance makes the shared
  behaviour invisible at the call site, and "which method did the subclass override?" is a worse
  question than "what is in the constructor?".
