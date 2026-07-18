# R13 — legacy oracle re-establishment (remediation plan 2)

Empirical baseline run answering re-review №2 findings F-11 (REGRESSED),
F-19, F-20, F-21 and producing the populated-adoption fixture for R19.
Findings and verdicts: `observations.md`.

## Rig identification

| Item | Value |
|---|---|
| Legacy artifact | `service/build/libs/mod-service-interaction-4.4.0-SNAPSHOT.jar` |
| Jar sha256 | `abb3721190394d2eea3e5661345b4d0fd8da89add558e0fc9924b26ec79b4c57` |
| JVM | OpenJDK 17 (`/usr/lib/jvm/java-17-openjdk-amd64`), Grails env `production` |
| Boot | `java -Ddb.host=localhost -Ddb.port=54321 -Ddb.database=okapi_modules_test -Ddb.username=folio_admin -Ddb.password=folio_admin -Dserver.port=8080 -jar <jar>` |
| Database | `postgres:18` via `tools/testing/docker-compose.yml` (container `testing_pg`, host port 54321), fresh volume (`docker compose down -v` first) |
| Tenants | `r13a` (run 1), `r13b` (run 2); enable = `POST /_/tenant` with `loadReference`/`loadSample` true → 201 |

## Procedure (in order)

1. `r13-observe.sh r13a run1` — populate request sequence in observation
   mode (statuses recorded, never asserted) + directed probe rows. Raw
   bodies + response headers per probe in `run1/`, manifest
   `run1/observations.tsv`, legacy stack traces in
   `run1/legacy-log-excerpt.log`.
2. `harness/populate.sh` legacy expectations corrected to the observed
   statuses (dashboard-create 200, widget-def-create 201 — see
   `observations.md`).
3. Run 2 on fresh tenant `r13b`:
   - corrected `populate.sh` (fail-closed) — **all steps passed**
     (`run2/populate-legacy/`);
   - `capture.sh legacy` — 23/23 probes, every pinned legacy expectation
     held, starred rows recorded (`run2/capture-legacy/manifest.json` with
     per-body sha256);
   - `r13-probes.sh r13b run2-directed` — directed rows, statuses
     **identical to run 1**.
4. `pg_dump --schema=r13b_mod_service_interaction` →
   `r13b-populated-schema.sql` (DDL + data; the R19 adoption fixture).
   Dumped after a post-JVM-restart attestation call so the schema carries
   its own `db_key_pair` row (see the D-5 note in `observations.md`).
   Per-table row counts: `run2/rowcounts.tsv`.

## Downstream use

- **R16** — governs D-2 re-correction, D-27, D-28..D-31 and pins the
  starred `expect_legacy` column of `probes.tsv` from these captures.
- **R18** — `run2/capture-legacy/` is the legacy half of the final
  evidence bundle (re-run against the same rig for freshness).
- **R19** — `r13b-populated-schema.sql` restored into the IT database is
  the adopted-schema upgrade fixture.
