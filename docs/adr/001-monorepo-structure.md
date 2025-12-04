# 1. Monorepo Structure

Date: 2025-12-04

## Status

Accepted

## Context

The project started with services at the root level, causing clutter and lack of clear boundaries. We needed a structure that supports:
- Multiple microservices (Auth, Gateway, Profile)
- Shared build logic (Gradle convention plugins)
- Centralized infrastructure (Docker, K8s)
- Clear API contracts (OpenAPI)

## Decision

We adopted a production-grade monorepo structure:

```
rido-backend/
├── build-logic/         # Shared Gradle plugins
├── services/            # Microservices source code
├── shared/              # Shared libraries (future)
├── openapi/             # API contracts
├── docker/              # Centralized Dockerfiles
├── infra/               # Local development infra
├── scripts/             # Dev and CI scripts
└── docs/                # Architecture documentation
```

## Consequences

### Positive
- **Isolation**: Services are clearly separated in `services/`.
- **Consistency**: Build logic is shared via convention plugins.
- **Discoverability**: Top-level folders clearly indicate purpose.
- **Portability**: Docker-first approach simplifies deployment.

### Negative
- **Complexity**: Slightly more complex initial setup than a flat repo.
- **Migration**: Required moving files and updating paths.
