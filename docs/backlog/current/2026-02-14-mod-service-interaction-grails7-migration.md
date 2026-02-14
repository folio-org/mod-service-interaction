# Backlog: mod-service-interaction Grails 7 Migration

- Date: 2026-02-14
- Component: `mod-service-interaction` (`service/`)
- Branch: `grails-7-upgrade`
- Goal: migrate `mod-service-interaction` from Grails 6 to Grails 7 using the upgraded local `web-toolkit-ce` and `grails-okapi` during migration, then remove local links before final publish.

## Core Principles

- Root-cause fixes over compatibility hacks.
- Keep security and tenant behavior invariant unless explicitly decided and test-locked.
- Migrate incrementally with clear checkpoints and session notes.
- Preserve schema-agnostic behavior (no test-fixture model coupling).
- Side goals:
  - Prefer Java over Groovy where straightforward.
  - Prefer Java POJO/controller endpoint definitions over Gson templates where straightforward.

## Current State (Baseline Inventory)

- Build layout: Gradle project under `service/`.
- Current versions (in-progress migration line):
  - `grailsVersion=7.0.7` (`service/gradle.properties`)
  - wrapper upgraded to Gradle `8.14.4` (`service/gradle/wrapper/gradle-wrapper.properties`)
  - settings plugin switched to `org.apache.grails.gradle.grails-web` (`service/settings.gradle`)
- Key migration dependency posture:
  - temporary local composite substitution active for `web-toolkit-ce` and `grails-okapi` (both Grails 7-upgraded locally)
  - Spring Security plugin moved to `org.grails.plugins:spring-security-core:7.0.1-SNAPSHOT` with transitive exclusions to avoid pulling a conflicting `org.grails` graph
  - transitional compatibility shim added: `org.grails:grails-web-sitemesh:6.2.4` (`transitive=false`) for `GroovyPageLayoutFinder`
  - `io.github.http-builder-ng:http-builder-ng-core:1.0.4` still present (follow-up modernization item)
- Gson templates present in `service/grails-app/views/**/*.gson`.
- Current gate result:
  - `GRADLE_USER_HOME="$PWD/.gradle-codex" ./gradlew clean check --stacktrace` => `PASS` (2026-02-14).
  - notable migration fixes completed:
    - Liquibase startup fixed (`Unknown command 'update'`) by moving to `database-migration:6.0.0-M1` and using a composite resource accessor in `ExtendedGrailsLiquibase`.
    - Spring Security compatibility stabilized on `7.0.1-SNAPSHOT` (with constrained transitive excludes) plus explicit Grails 7 compatibility shims.
    - legacy semver parsing crash on unresolved app version placeholders fixed with safe fallback in `AppFederationService`.
    - checksum/refdata binding restored for number-generator flows by reintroducing explicit `@Defaults(...)` for `checkDigitAlgo`.
    - transient test-environment `java.util.prefs` lock warnings still appear in Gradle output but do not fail the build.

## Migration Decisions (Current)

- D1 (Accepted): Use local composite build substitutions during migration only:
  - `../../web-toolkit-ce` for `com.k_int.grails:web-toolkit-ce`
  - `../../grails-okapi` for `com.k_int.okapi:grails-okapi`
- D2 (Accepted): Local project links are temporary and must be removed before final push/release.
- D3 (Accepted): Track migration in this backlog document as handoff context across sessions.

## Work Plan

### 1) Baseline and Toolchain Alignment
- [x] Run baseline `clean check` and capture blockers.
- [x] Align Gradle wrapper/Grails plugin/dependency matrix to a Grails 7-compilable baseline.
- [x] Re-run baseline gates (`test`, `check`).

### 2) Dependency and Plugin Migration
- [x] Add temporary local composite references to upgraded `web-toolkit-ce` and `grails-okapi`.
- [x] Upgrade Grails plugin IDs/versions and Grails dependencies to Grails 7 line.
- [x] Address Spring Security/database-migration compatibility issues.

### 3) Runtime Compatibility and Behavior Locks
- [x] Ensure security/auth/tenant flows remain behavior-compatible.
- [ ] Add/adjust characterization tests where behavior is high-risk.
- [ ] Resolve `http-builder-ng` usage in module code paths (if any).

### 4) API Rendering Modernization (NFR)
- [ ] Inventory Gson template usage by endpoint.
- [ ] Identify low-risk endpoints suitable for POJO/controller-based rendering.
- [ ] Migrate only straightforward cases during upgrade window; defer larger rewrites.

### 5) Downstream Guidance and Closeout
- [ ] Create `mod-service-interaction/grails7-upgrade.md`.
- [ ] Link it from `mod-service-interaction/README.md`.
- [ ] Remove temporary local composite links in `service/settings.gradle` before final push.
- [ ] Run final closeout gate:
  - `GRADLE_USER_HOME="$PWD/.gradle-codex" ./gradlew clean check --rerun-tasks --no-build-cache --stacktrace`

## Verification Commands

- Baseline:
  - `cd service && GRADLE_USER_HOME="$PWD/.gradle-codex" ./gradlew clean check --stacktrace`
- Full check:
  - `cd service && GRADLE_USER_HOME="$PWD/.gradle-codex" ./gradlew check --stacktrace`
- Final closeout:
  - `cd service && GRADLE_USER_HOME="$PWD/.gradle-codex" ./gradlew clean check --rerun-tasks --no-build-cache --stacktrace`

## Session Notes

- 2026-02-14:
  - created branch `grails-7-upgrade`
  - created backlog tree `docs/backlog/current`
  - added temporary local composite substitutions in `service/settings.gradle` for upgraded local `web-toolkit-ce` and `grails-okapi`
  - updated root workspace preferences in `AGENTS.md` with NFR: prefer Java POJO/controller endpoint specifications over Gson templates where straightforward.
  - executed baseline gate:
    - `cd service && GRADLE_USER_HOME="$PWD/.gradle-codex" ./gradlew clean check --stacktrace`
    - failed on Gradle 7 / Java mismatch (`Unsupported class file major version 65`)
  - upgraded wrapper distribution in `service/gradle/wrapper/gradle-wrapper.properties`:
    - `gradle-7.6.3-bin.zip` -> `gradle-8.14.4-bin.zip`
  - re-ran baseline gate on Gradle 8.14.4:
    - plugin/configuration failure before compile/test:
      - `Failed to apply plugin 'org.grails.plugins.views-json'`
      - dependency-management resolution path reports `Cannot resolve ... because no repositories are defined` during early configuration.
  - conclusion: next step is full Grails 7 plugin/dependency alignment (not just wrapper bump).
  - continued migration and reached integration-test runtime:
    - added missing `micronautPlatformVersion` and `grailsTestingVersion` in `service/gradle.properties`
    - upgraded/realigned Grails 7 plugin IDs and dependency coordinates in `service/build.gradle`
    - replaced legacy Geb coordinate with `org.gebish:geb-spock:7.0` and Selenium 4 artifacts
    - added local integration-test helper `service/src/integration-test/groovy/com/k_int/web/toolkit/testing/HttpSpec.groovy` because upstream helper is not published from plugin test sources
    - removed explicit legacy Logback pinning that conflicted with SLF4J 2 and Boot 3 logging bootstrap
    - added transitional `org.grails:grails-web-sitemesh:6.2.4` (`transitive=false`) for missing `GroovyPageLayoutFinder`
    - moved Spring Security plugin to `7.0.1-SNAPSHOT` (with transitive exclusions) to fix PBKDF2 constructor ambiguity under Spring Security 6
    - removed legacy Undertow `2.2.x` override entries (Boot 3 now provides Jakarta-compatible Undertow)
    - migrated remaining Jakarta namespace breakpoints:
      - `javax.annotation.Resource` -> `jakarta.annotation.Resource`
      - `javax.persistence.*` -> `jakarta.persistence.*`
  - with Postgres available, completed blocker burn-down:
    - upgraded `org.grails.plugins:database-migration` to `6.0.0-M1` in both `mod-service-interaction` and local `grails-okapi`
    - updated `grails-okapi` Liquibase accessor (`ExtendedGrailsLiquibase`) to use `CompositeResourceAccessor` (Spring + classloader)
    - added Grails 7 compatibility shim class `grails.async.web.AsyncGrailsWebRequest` in local `grails-okapi`
    - added fallback handling for unresolved app version placeholders in `grails-okapi` `AppFederationService.getFamilyName()`
    - added Spring bean shim for `sitemesh3Secured` in `service/grails-app/conf/spring/resources.groovy`
    - reintroduced explicit checksum algorithm defaults in `NumberGeneratorSequence.checkDigitAlgo` to restore refdata binding during request payload binding
  - gate results:
    - focused: `./gradlew clean integrationTest --tests org.olf.AttestedAssertionSpec --stacktrace` => PASS
    - focused: `./gradlew clean integrationTest --tests org.olf.NumberGeneratorSpec --stacktrace` => PASS
    - full: `./gradlew clean check --stacktrace` => PASS
    - closeout: `./gradlew clean check --rerun-tasks --no-build-cache --stacktrace` => PASS
  - attempted publish-readiness step (remove local composite links in `service/settings.gradle`):
    - result: `./gradlew clean check --stacktrace` => FAIL at `:compileGroovy`
    - failure: `java.lang.NoClassDefFoundError: javax.servlet.ServletRequest`
    - implication: currently published dependency line (`com.k_int.okapi:grails-okapi:7.5.3`) is not yet aligned with the Grails 7 local fixes used during migration sessions.

## Status

- Status: `OPEN`
- Next action: publish Grails 7-compatible `grails-okapi` / `web-toolkit-ce` artifacts (or update to published versions containing these fixes), then retry removal of local composite links.
