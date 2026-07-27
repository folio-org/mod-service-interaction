# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

`mod-service-interaction` is a FOLIO backend module (Java 21 / Spring Boot / folio-spring-base) providing cross-app connectivity for the FOLIO ecosystem:

- **Dashboards & widgets** — user-configurable dashboards composed of widgets, with per-dashboard access levels (`view`/`edit`/`manage`).
- **Number generators** — configurable sequence generators used across other FOLIO modules (barcodes, request numbers, vendor codes, etc.), supporting prefixes/postfixes, check digits (EAN13, ISBN10, ISSN, Luhn, mod10 variants), max-value thresholds/warnings, and a `${current_year}`-token year-based reset mechanism.
- **RFC 8693 attested assertions** — signs short-lived RS256 JWTs for module-to-module federated trust, backed by per-tenant keypair storage in the DB (nimbus-jose-jwt).

The module is **spec-first**: `specs/` is the governed source of truth (requirements, decisions, OpenAPI contracts, models, behavior, traceability). The served API surface is generated at build time from `specs/api/*.yaml`, consumed unmodified (ADR-004).

`service/` (the retired Grails implementation) and `docs/migration/` are scheduled for removal — never modify or build against them. Until that cleanup lands, note that `AdoptedSchemaUpgradeIT` loads its ground-truth fixture from `docs/migration/evidence/r13-legacy/` — relocate the fixture before deleting that tree or the build breaks.

## Commands

All Maven commands run from the repo root:

```bash
mvn clean verify                               # full build: unit tests (*Test) + integration tests (*IT)
mvn verify -Dit.test=NumberGeneratorParityIT   # run a single integration test
mvn clean verify -DskipITs                     # compile + unit tests only
mvn spring-boot:run                            # run locally (env vars below)
docker build -t mod-service-interaction .      # image from target/*.jar (build the jar first)
```

- Integration tests boot the full app against a Testcontainers Postgres (`postgres:16-alpine`) — Docker must be running; no manual DB setup or docker-compose needed.
- Runtime env: `DB_HOST`/`DB_PORT`/`DB_DATABASE`/`DB_USERNAME`/`DB_PASSWORD`, `OKAPI_URL`. The app serves on port 8080.
- `target/ModuleDescriptor.json` is generated at build time from `descriptors/ModuleDescriptor-template.json` (maven-resources filtering + copy-rename; the output is deterministic — byte-identical across rebuilds of the same source). Edit the template, never a generated file.

## Architecture

### Layout (`src/main/java/org/folio/servint`)

- `controller/` — implements the openapi-generator interfaces produced from `specs/api/`; error-envelope shapes are centralized in `ServintExceptionHandlers`.
- `service/{dashboard,widget,numgen,attestation,refdata}/` — business logic per area.
- `domain/entity/`, `repository/`, `mapper/` — JPA entities, Spring Data repositories, MapStruct mappers.
- `web/` — the KIWT listing grammar (`KiwtListing`, `KiwtFilterParser`): the legacy-compatible `filters=`/`match=`/`term=`/`sort=`/`stats=` query surface shared by all listing endpoints. `KiwtListingGrammarIT` is its executable documentation — check it before touching parser logic.
- `client/` — cross-module federation calls via folio-spring's Okapi-enriched RestClient stack (`folio.exchange.enabled`, ADR-008).

### Multi-tenancy & migrations

- Schema-per-tenant via folio-spring: the schema name `<tenant>_mod_service_interaction` derives from `spring.application.name`, which must stay exactly `mod-service-interaction` (ADR-006).
- Liquibase master changelog: `src/main/resources/db/changelog/changelog-master.xml`. The `adoption-baseline*` changesets are precondition-guarded so that enabling the module on a schema created by the previous Grails implementation marks them as already run and adopts the existing data losslessly (`AdoptedSchemaUpgradeIT` proves this against a populated ground-truth fixture). Never edit shipped changesets — add new ones.
- The `/_/tenant` surface is folio-spring-base's `TenantController` (intentionally **not** generated from a spec): enable runs Liquibase and seeds refdata + default number generators; disable evicts per-tenant caches; purge is strictly gated — it must be an explicit boolean `purge: true`, anything else is rejected rather than defaulting to destruction.

### Operational surface

- Unauthenticated management endpoints under `/admin`: **health + metrics only**. `loggers` is deliberately excluded (an unauthenticated loggers endpoint lets anyone reaching the port flip log levels); `ManagementSurfaceIT` pins the exact surface — extend it as a conscious decision, not by config drift.
- The per-tenant widget-definition cache (Caffeine) is bounded by `folio.widgets.definition-cache.*` (max tenants + expiry); its `cache.*` meters are tagged `cache=widget-definition-tenant-cache` and scrapeable via `/admin/metrics`.
- `scripts/k8s_deployment_template.yaml` is kept in lockstep with `application.yml` (port, probes, NetworkPolicy) by `K8sDeploymentTemplateTest`.

## Spec governance (SDD)

- **Never hand-edit files under `specs/`.** Every spec change goes through a governed SDD session — invoke the `sdd-session` skill (or `sdd-ingest` / `sdd-impl-trace` for their respective workflows), which stages, validates, and commits atomically.
- Run `sdd validate --semantic --branch <branch>` **from the repo root**. Run from anywhere else, the semantic lane goes silently inert — exit 0 with `gate_status: UNCALIBRATED` — so never trust the exit code alone; check the reported gate status.
- `sdd.config.yaml` at the root configures the spec graph (Neo4j) and the semantic judge lane.

## Testing conventions

- All tests live under `src/test/java/org/folio/servint`. Failsafe runs `*IT` classes (full app + Testcontainers Postgres); Surefire runs `*Test` classes (no container, e.g. `K8sDeploymentTemplateTest`, `SchemaNameParityTest`).
- Many ITs use `@TestMethodOrder(OrderAnnotation.class)` and share container/tenant state across methods in declared order — don't reorder test methods assuming independence.
- Parity ITs double as executable documentation: `KiwtListingGrammarIT` (listing grammar, including deliberately preserved legacy quirks), `NumberGeneratorParityIT` (check-digit algorithms, `outputTemplate`/`preChecksumTemplate`, `${current_year}` token — check expected input/output pairs before changing generator logic), `ErrorEnvelopeMatrixIT` (error shapes).
- Some legacy behaviors are **contracts, not bugs**: e.g. missing primary keys on `dashboard_access`/`dashboard_display_data`, or the broken `$`-wildcard transform in the listing grammar. They are pinned by tests and registered as deliberate decisions — don't "fix" them without a spec-level decision.
