# Architecture Decision Records

One page per decision that was not obvious, written at the time it was made. Each says what the
alternatives were and what the choice costs, because a decision recorded without its trade-off
is just an assertion.

| # | Decision | Status |
| --- | --- | --- |
| [001](001-hexagonal-layout.md) | Light hexagonal layout, enforced by ArchUnit | Accepted |
| [002](002-self-signed-jwt.md) | Self-signed JWT instead of an identity provider | Accepted |
| [003](003-no-database.md) | No database; Caffeine cache instead | Accepted |
| [004](004-in-memory-pagination.md) | Paginate Films and searches in memory | Accepted |
| [005](005-generic-output-port.md) | One generic output port, one adapter per entity | Accepted |
| [006](006-registration-behind-a-profile.md) | Registration behind a profile, not in the core | Accepted |
