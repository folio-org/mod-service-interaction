# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

`mod-service-interaction` is a FOLIO backend module (Grails 6 / Groovy) providing cross-app connectivity for the FOLIO ecosystem. Its main responsibilities:

- **Dashboards & widgets** — user-configurable dashboards (`Dashboard`, `DashboardAccess`, `DashboardDisplayData`) composed of widgets (`WidgetDefinition`, `WidgetType`, `WidgetInstance`), with per-dashboard access levels (`view`/`edit`/`manage`).
- **Number generators** (`org.olf.numgen`) — configurable sequence generators used across other FOLIO modules (barcodes, request numbers, vendor codes, etc.), supporting prefixes/postfixes, check digits (EAN13, ISBN10, ISSN, Luhn, mod10 variants), max-value thresholds/warnings, and a `${current_year}`-token year-based reset mechanism.
- **RFC 8693 attested assertions** (`org.olf.rfc8693`) — signs short-lived JWTs (RS256) to support module-to-module federated trust, backed by per-tenant `DBKeyPair` storage.

All business logic lives under `service/` (the actual Grails app); the repo root only holds packaging/CI/docs.

## Commands

All Gradle commands are run from `service/`:

```bash
cd service

./gradlew bootRun                 # run the app (needs OKAPI_SERVICE_HOST/PORT + a Postgres db; see README env vars)
./gradlew assemble                # compile + package (no tests)
./gradlew buildImage               # build the Docker image (uses com.bmuschko docker plugin)

./gradlew integrationTest                                   # run the full Spock/Geb integration suite
./gradlew integrationTest --tests "org.olf.NumberGeneratorSpec"   # run a single spec
```

There is no meaningful unit test suite — all tests are integration specs under `src/integration-test/groovy` that boot the full app against a real Postgres instance. Before running `integrationTest`, start the DB (and the `run-int-tests.yml` CI job does this via docker-compose):

```bash
cd tools/testing
docker compose up -d
# ... run tests from service/ ...
docker compose down -v
```

Local dev against a full Okapi stack (vagrant) is documented in the root `README.md`; `scripts/register_and_enable*.sh` and `scripts/run_external_reg.sh` handle module registration against a running Okapi.

## Architecture

### Okapi module conventions

- Controllers extend `OkapiTenantAwareController<T>` (from `com.k_int.okapi:grails-okapi` / `com.k_int.grails:web-toolkit-ce`) and are annotated `@CurrentTenant`. Common inherited helpers used throughout: `doTheLookup(DomainClass) { ...criteria... }` for filtered listing, `getObjectToBind()` for the parsed request body, `updateResource(instance)`, and `getPatron()` for the calling user.
- Routing is centralized in `grails-app/controllers/org/olf/UrlMappings.groovy` — REST resources plus bespoke collection sub-routes (e.g. `/servint/dashboard/my-dashboards`, `/servint/numberGenerators/getNextNumber`).
- The Okapi `ModuleDescriptor` is generated at build time from `src/main/okapi/ModuleDescriptor-template.json` (interpolated with `appVersion`/`okapiInterfaceVersion` from `gradle.properties`) — permission names follow `servint.<area>.<verb>`. Edit the template, not a generated file.
- `BootStrap.groovy` fails loudly (logs, doesn't throw) if `module-tenant-changelog.groovy` isn't on the classpath — that's the signal a build didn't package migrations correctly.

### Multi-tenancy

- GORM multi-tenancy mode is `SCHEMA` (one Postgres schema per tenant), resolved via `com.k_int.okapi.OkapiTenantResolver`. Domain classes implement `grails.gorm.MultiTenant<T>`.
- `HousekeepingService` runs at the **module** level (no tenant context) and subscribes to `okapi:dataload:reference` (`@Subscriber`) to seed refdata/default number generators into a tenant schema on load — it manually enters tenant context via `Tenants.withId(...)`.
- Cross-cutting controlled-vocabulary fields use the `com.k_int.web.toolkit.refdata` pattern: `@CategoryId`/`@Defaults` annotations on domain fields, `RefdataValue.lookupOrCreate(category, label, value)` to seed/fetch values.
- Liquibase changesets live in `grails-app/migrations/`, chained from the master changelog `module-tenant-changelog.groovy`.

### Known operational gotchas (see root `README.md` for full detail)

- Federated changelog locks (`federation_lock`, `system_changelog_lock` tables) and Hikari connection-pool starvation are recurring upgrade-time failure modes on this module — documented at length in the README if you're debugging a stuck tenant upgrade rather than writing new code.

### Testing conventions

- Specs extend `BaseSpec` (`src/integration-test/groovy/org/olf/BaseSpec.groovy`), which extends `HttpSpec` (web-toolkit testing support) and is `@Stepwise` — test methods within a spec run in declaration order and share state (e.g. entities created in one `void "..."` method are used in later ones). Don't reorder test methods assuming independence.
- `BaseSpec` purges and recreates a fresh tenant per spec class (named after the spec's simple class name) in `setupSpec`/the first two stepwise methods.
- Number generator tests in particular double as executable documentation of check-digit algorithms and templating (`outputTemplate`, `preChecksumTemplate`, `${current_year}` token) — check `NumberGeneratorSpec` for expected input/output pairs before changing generator logic.
