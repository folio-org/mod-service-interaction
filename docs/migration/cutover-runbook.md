# Production cutover runbook — mod-service-interaction 4.4.x (Grails) → 5.0.0 (Spring Boot)

Executable, phase-by-phase procedure for cutting a FOLIO installation over
from the legacy Grails module to the Spring Boot port, and for rolling back.
Written for an engineer with no prior context (review F-12 remediation):
every step is a command, an expected output, and an on-failure action.

The port adopts the legacy database **in place**: same Postgres, same
per-tenant schemas, no export/import, no data migration scripts.

**Before production:** execute this runbook once end-to-end (cutover *and*
rollback, Phases 0–7) on a rehearsal rig with a staging copy of production
data. The in-place adoption and storage-level rollback compatibility are
proven by the repo's evidence (below); the *Okapi-orchestrated* end-to-end
flow was not exercised by M4 (review C10 — UNVERIFIABLE) and this rehearsal
is what closes that gap.

## Why in-place adoption is safe (evidence)

1. **Adoption changesets never touch legacy data.** All 14 changesets in
   `src/main/resources/db/changelog/changes/adoption-baseline-*.xml` carry
   `tableExists` preconditions with `onFail="MARK_RAN"`. Legacy Liquibase
   state lives in `tenant_changelog`/`tenant_changelog_lock` — the port's
   `databasechangelog` starts empty in an adopted schema, so every
   precondition fires and every changeset is marked ran with **zero DDL
   against business tables**. Proven: 14/14 MARK_RAN; business tables,
   constraints, and rows unchanged. The enable pass does create the port's
   Liquibase bookkeeping tables (`databasechangelog`/`databasechangeloglock`)
   — expected additions, invisible to legacy (review F-10), so a *whole*-
   schema catalog diff is not empty; exclude those two tables (the
   verification SQL in Phase 2/3 does).
2. **Fresh-tenant DDL matches legacy DDL** for every port-owned table —
   columns, column defaults, *and* constraints, constraint names included
   (down to Hibernate-generated FK names) — modulo one benign physical
   column-ordinal delta (`dashboard.dshb_description`, ordinal 4 vs legacy
   5; see `wire-compat-deviations.md` D-16). Verified by catalog diff over
   name-sorted column sets. (The review's F-10 `DEFAULT false` finding on
   `refdata_category.internal` was fixed in workstream R7 — the adoption
   changelog now declares no default, matching legacy.)
3. **Reference-data seeding is idempotent** against legacy-seeded data
   (refdata categories/values, default number generators, dashboard access
   values): row counts unchanged after the port's tenant-enable.
4. **Wire compatibility** is evidenced by the capture harness
   (`docs/migration/harness/`): per-probe manifests with pinned statuses and
   body hashes, diffed against the registered deviation dossier
   (`wire-compat-deviations.md`, D-1..D-19). Parity claims are made by
   `diff-runs.sh` output, not by prose counts (review C1/F-11).
5. **Rollback safety at the storage level**: data written through the port
   was read and extended by the legacy module in M4 (a port-generated
   sequence number `m4--00004` was followed by legacy `m4--00005`); the port
   never writes `tenant_changelog`, which is what makes rollback trivial.

## Conventions used below

```bash
export OKAPI_URL=http://okapi:9130            # your Okapi
export OKAPI_TOKEN=...                        # supertenant token if Okapi is secured
export TENANT=diku                            # tenant being cut over
export SCHEMA="${TENANT}_mod_service_interaction"
export DB_HOST=... DB_PORT=5432 DB_DATABASE=okapi_modules DB_USERNAME=folio_admin
export PGPASSWORD=...                         # for psql/pg_dump
PSQL=(psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USERNAME" -d "$DB_DATABASE")
mkdir -p evidence backup
```

- Module ids: `mod-service-interaction-5.0.0` (port), and the exact legacy id
  currently enabled — discover it, don't guess:
  `curl -s "$OKAPI_URL/_/proxy/tenants/$TENANT/modules" | jq -r '.[].id' | grep service-interaction`
  (referred to as `mod-service-interaction-4.4.x` below).
- Add `-H "X-Okapi-Token: $OKAPI_TOKEN"` to every `curl` against Okapi in a
  secured installation; add `-H "X-Okapi-Tenant: ..."` where shown.
- Every command that gathers evidence writes into `evidence/`; keep that
  directory as the per-cutover audit trail alongside a ledger (Phase 4).

---

## Phase 0 — Preconditions

**0.1 Tenant inventory.**

```bash
"${PSQL[@]}" -Atc "SELECT nspname FROM pg_namespace
  WHERE nspname LIKE '%_mod_service_interaction' ORDER BY 1" \
  | tee evidence/tenant-schemas.txt
```
Expected: one schema per tenant (`<tenant>_mod_service_interaction`).
On failure (empty): wrong database — stop.

**0.2 Legacy health and stuck locks** (known legacy failure modes, root
`README.md`):

```bash
"${PSQL[@]}" -c "SELECT * FROM ${SCHEMA}.tenant_changelog_lock WHERE locked = true;"
"${PSQL[@]}" -c "SELECT * FROM public.federation_lock;" 2>/dev/null || true
```
Expected: 0 rows locked. On failure: resolve the legacy lock first (root
README "known gotchas") — do not start a cutover over a stuck legacy state.

**0.3 Register the port's ModuleDescriptor** (built from
`descriptors/ModuleDescriptor-template.json`, `@artifactId@`/`@version@`
resolved by the Maven build; the build emits `target/ModuleDescriptor.json`):

```bash
curl -sw '\n%{http_code}\n' -X POST "$OKAPI_URL/_/proxy/modules" \
  -H 'Content-Type: application/json' -d @target/ModuleDescriptor.json
```
Expected: `201` (or `400 … already exists` on re-run — fine).
The interface surface is unchanged for consumers (`servint 4.4`,
`dashboard 1.0`, `_timer 1.0`; see Appendix B) — **no dependent module or UI
bundle needs changes**. The `_tenant` interface is deliberately 2.0 (D-17,
ADR-012).

**0.4 Make the 5.0.0 container deployable** through your orchestration
(Okapi-managed deployment can use the included `launchDescriptor`; sizing in
Appendix A). Config translation: the port reads
`DB_HOST`/`DB_PORT`/`DB_DATABASE`/`DB_USERNAME`/`DB_PASSWORD` (folio-spring
convention) instead of the legacy `db.*` system properties, and listens on
**8081**. Verify one instance answers:

```bash
curl -s http://<port-instance>:8081/admin/health
```
Expected: `{"status":"UP"}`. On failure: fix deployment before touching any
tenant.

**0.5 Version discipline.** Go straight from 4.4.x to the **final** 5.0.0
build. Enabling interim port builds and then upgrading breaks Liquibase
checksums (MARK_RAN rows store the md5sum of the changeset text at enable
time). Recovery from that state: Phase 7.

---

## Phase 1 — Backup and restore drill

**1.1 Per-tenant schema dump** (repeat per tenant, or loop over
`evidence/tenant-schemas.txt`):

```bash
STAMP=$(date -u +%Y%m%dT%H%M%SZ)
pg_dump -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USERNAME" -d "$DB_DATABASE" \
  --schema "$SCHEMA" --format=custom --no-owner \
  --file "backup/${SCHEMA}.${STAMP}.dump"
pg_restore --list "backup/${SCHEMA}.${STAMP}.dump" | grep -c 'TABLE DATA'
```
Expected: `pg_dump` exits 0; the `grep -c` prints the number of tables with
data (> 0). On failure: do not proceed — a cutover without a verified backup
is not authorized by this runbook.

**1.2 Restore drill** (once per cutover campaign, on the rehearsal rig —
proves the dump actually restores):

```bash
createdb -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USERNAME" msi_restore_drill
pg_restore -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USERNAME" -d msi_restore_drill \
  --exit-on-error "backup/${SCHEMA}.${STAMP}.dump"
psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USERNAME" -d msi_restore_drill \
  -Atc "SELECT count(*) FROM ${SCHEMA}.number_generator;"
dropdb -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USERNAME" msi_restore_drill
```
Expected: `pg_restore` exits 0; the count matches the source schema's.
On failure: the backup is not usable — fix before any cutover.

---

## Phase 2 — Pre-cutover evidence capture (per tenant)

**2.1 Business-schema catalog** (bookkeeping tables excluded; sorted by
column *name* because physical ordinals legitimately differ, D-16):

```bash
"${PSQL[@]}" -Atc "SELECT table_name||'|'||column_name||'|'||data_type||'|'||
    is_nullable||'|'||coalesce(column_default,'')
  FROM information_schema.columns
  WHERE table_schema='$SCHEMA'
    AND table_name NOT IN ('databasechangelog','databasechangeloglock')
  ORDER BY table_name, column_name" > "evidence/$TENANT-catalog-before.txt"
wc -l "evidence/$TENANT-catalog-before.txt"
```
Expected: non-empty file. On failure: schema name wrong — recheck 0.1.

**2.2 Row counts per table:**

```bash
"${PSQL[@]}" -Atc "SELECT format('SELECT %L || ''|'' || count(*) FROM %I.%I;',
    table_name, table_schema, table_name)
  FROM information_schema.tables
  WHERE table_schema='$SCHEMA' AND table_type='BASE TABLE'
    AND table_name NOT IN ('databasechangelog','databasechangeloglock')
  ORDER BY table_name" | "${PSQL[@]}" -At > "evidence/$TENANT-counts-before.txt"
wc -l "evidence/$TENANT-counts-before.txt"
```
Expected: one `table|count` line per table. On failure: as 2.1.

**2.3 (Rehearsal rig only) wire baseline** with the harness:
`docs/migration/harness/capture.sh legacy` against the legacy module
(see the harness README for the exact boot/enable commands). Skip in
production — Phase 3's smoke covers the live check without touching
production generators.

---

## Phase 3 — Canary cutover (one low-traffic tenant)

Pick a small, low-traffic canary tenant. Everything in this phase is
per-tenant and is reused by the batch loop in Phase 4.

**3.1 Quiesce.** Okapi switches routing atomically when the new module
version is enabled; in-flight legacy requests complete on the legacy
instance. For extra caution on the canary, announce a short maintenance
window; there is no queue/drain mechanism to operate in this module.

**3.2 Enable the port:**

```bash
curl -sw '\n%{http_code}\n' -X POST \
  "$OKAPI_URL/_/proxy/tenants/$TENANT/install?deploy=true" \
  -H 'Content-Type: application/json' \
  -d '[{"id":"mod-service-interaction-5.0.0","action":"enable"}]' \
  | tee "evidence/$TENANT-enable.json"
```
Expected: `200` and a body echoing
`[{"id":"mod-service-interaction-5.0.0","action":"enable","from":"mod-service-interaction-4.4.x"}]`
(the `from` field appears on upgrades). Duration: seconds — the tenant work
is the Liquibase MARK_RAN pass plus idempotent seeding.

What Okapi does under the hood (run this directly against a port instance
only on a rig without Okapi):

```bash
curl -sw '\n%{http_code}\n' -X POST "http://<port-instance>:8081/_/tenant" \
  -H "X-Okapi-Tenant: $TENANT" -H 'Content-Type: application/json' \
  -d '{"module_from":"mod-service-interaction-4.4.x","module_to":"mod-service-interaction-5.0.0"}'
```
Expected: `204` — the `_tenant` 2.0 contract completes synchronously (D-17).
The other 2.0 shapes, for reference: `GET /_/tenant/{id}` → `200` `"true"`;
`DELETE /_/tenant/{id}` → `204` (no-op); purge is
`POST /_/tenant {"module_from":"…","purge":true}` (drops schema + role —
**never** part of cutover).

On failure (either call): capture the response body and the port instance
log; if the log shows a Liquibase lock/checksum error go to Phase 7;
otherwise abort this tenant (Phase 6 — legacy is still enabled if the
install call failed, so "rollback" is usually just *not proceeding*).

**3.3 Post-enable database verification:**

```bash
"${PSQL[@]}" -c "SELECT exectype, count(*) FROM ${SCHEMA}.databasechangelog
                 GROUP BY exectype;"
```
Expected (adopted tenant):
```
 exectype | count
----------+-------
 MARK_RAN |    14
```
(Fresh, post-cutover tenants show `EXECUTED | 14` instead. Any `EXECUTED`
row on an *adopted* tenant means a precondition did not fire — abort, Phase
6, and investigate before any further tenant.)

Re-run 2.1 and 2.2 into `…-after.txt`, then:

```bash
diff "evidence/$TENANT-catalog-before.txt" "evidence/$TENANT-catalog-after.txt"
diff "evidence/$TENANT-counts-before.txt"  "evidence/$TENANT-counts-after.txt"
```
Expected: both diffs empty, exit 0. (Bookkeeping tables are already
excluded; legacy residue tables — D-16 — are included and must be
unchanged.) On failure: abort → Phase 6. A non-empty diff here is exactly
the signal this procedure exists to catch.

**3.4 Wire smoke — through Okapi, so routing is proven.**

> **WARNING:** `GET /servint/numberGenerators/getNextNumber` **consumes a
> number** — it permanently advances the sequence. Never smoke-test it
> against a production generator. The smoke below creates a dedicated
> throwaway generator, generates from it, and deletes it.

Read-only checks first:

```bash
OK=(-H "X-Okapi-Tenant: $TENANT" -H "X-Okapi-Token: $OKAPI_TOKEN")
curl -sw '\n%{http_code}\n' "${OK[@]}" \
  "$OKAPI_URL/servint/refdata/NumberGeneratorSequence/checkDigitAlgo?perPage=100"
curl -sw '\n%{http_code}\n' "${OK[@]}" "$OKAPI_URL/servint/settings/appSettings?perPage=10"
curl -sw '\n%{http_code}\n' "${OK[@]}" "$OKAPI_URL/servint/numberGenerators?perPage=10"
```
Expected: `200` each, non-empty JSON arrays (checkDigitAlgo values carry
`owner`, byte-compatible with legacy — M4).

Throwaway-generator write/generate/delete cycle:

```bash
SMOKE="smoke-$(date -u +%Y%m%dT%H%M%SZ)"
GEN=$(curl -s "${OK[@]}" -X POST "$OKAPI_URL/servint/numberGenerators" \
  -H 'Content-Type: application/json' \
  -d '{"code":"'"$SMOKE"'","name":"Cutover smoke — delete me"}' | jq -r .id)
echo "GEN=$GEN"                                        # expect a UUID, not null
SEQ=$(curl -s "${OK[@]}" -X POST "$OKAPI_URL/servint/numberGeneratorSequences" \
  -H 'Content-Type: application/json' \
  -d '{"owner":{"id":"'"$GEN"'"},"code":"smoke","name":"smoke","format":"00000","nextValue":1}' | jq -r .id)
echo "SEQ=$SEQ"                                        # expect a UUID, not null
curl -sw '\n%{http_code}\n' "${OK[@]}" \
  "$OKAPI_URL/servint/numberGenerators/getNextNumber?generator=$SMOKE&sequence=smoke"
# expect: {"nextValue":"00001"} then 200
curl -sw '%{http_code}\n' "${OK[@]}" -X DELETE "$OKAPI_URL/servint/numberGeneratorSequences/$SEQ"
curl -sw '%{http_code}\n' "${OK[@]}" -X DELETE "$OKAPI_URL/servint/numberGenerators/$GEN"
# expect: 204 and 204
```
On any unexpected status/null id: abort → Phase 6. (The calling user needs
`servint.numberGenerator.manage` for the deletes.)

**3.5 Health and logs:**

```bash
curl -s http://<port-instance>:8081/admin/health      # expect {"status":"UP"}
```
Watch the port instance log (first enable + first traffic). Red flags:
`ERROR`-level entries on `/servint` routes, `LiquibaseException`,
`HikariPool.*Connection is not available`, `OutOfMemoryError`, stack traces
on requests that return 5xx. Container: watch RSS vs the 670 MiB limit and
restarts/OOMKilled (Appendix A).

**3.6 Canary soak.** Leave the canary on 5.0.0 for an agreed window
(recommended: 24 h, covering the `_timer` firing — Phase 5) before batch
rollout.

**Abort criteria (any one → Phase 6):**
- 3.2 enable fails, or MARK_RAN ≠ 14 on an adopted tenant;
- any non-empty diff in 3.3;
- any smoke failure in 3.4;
- `/admin/health` not `UP` for more than 2 minutes;
- recurring 5xx on `/servint` routes that legacy did not produce;
- container restart loop or OOMKilled;
- sustained container RSS > 90% of the limit (Appendix A).

---

## Phase 4 — Batch rollout

Roll out in waves after a clean canary soak: e.g. 10 tenants, then 50, then
the remainder. The wave loop is a checked-in executable —
`docs/migration/harness/rollout.sh` — do not improvise it.

**The cutover is the checked step.** In its default `MODE=okapi`, the
script enables each tenant **through Okapi's install API**
(`POST $OKAPI_URL/_/proxy/tenants/<T>/install?deploy=true`, the 3.2 call),
then *verifies* the routing switch
(`GET /_/proxy/tenants/<T>/modules` must list `MODULE_TO`), then smokes
**through Okapi**, and only then appends a `complete` PASS ledger row
carrying the observed routing state. A tenant is never reported as cut
over on module-direct evidence alone. `MODE=direct` (module-direct
`_tenant` 2.0 POST, for a rig without Okapi) is an explicit opt-in
pre-verification pass: its ledger rows say `verified-direct`, never
`complete` — after a direct pass, the okapi-mode wave is still the
cutover.

Per tenant, in order, with a return-code check after every step: optional
wire pre-capture via `capture.sh`, the Phase 2.1/2.2 catalog + row-count
pre-snapshot, the enable per mode (a transport error is reconciled against
actual state — Okapi's module list, or the `databasechangelog` row count —
before it is classified), routing verification (okapi mode), the Phase 3.3
post-enable verification (catalog unchanged for adopted schemas —
`catalog.diff` is kept as evidence even when empty; business row counts
must not shrink; `databasechangelog` MARK_RAN-only on adopted tenants),
and a fixed read-only smoke (tenant-neutral `GET` probes with pinned
statuses — never `getNextNumber`, 3.4). `/admin/health` and Okapi
reachability are checked once per run before the first tenant (3.5). A
tenant with no pre-state (schema absent) is recorded as fresh and the
catalog/count comparison is skipped — the decision itself is logged.

```bash
cd docs/migration/harness
# rehearse the wave configuration first: prints every curl/psql command and
# every ledger row, executes nothing, exits 0 on a well-formed config —
# and exits 2 on a malformed one (bad module id, non-JSON TENANT_PARAMETERS,
# bad tenant list), so the rehearsal itself is a config gate
DRY_RUN=true OKAPI_URL=http://<okapi>:9130 PORT_URL=http://<port-instance>:8081 \
  MODULE_TO=mod-service-interaction-5.0.0 MODULE_FROM=mod-service-interaction-4.4.x \
  PGHOST=$DB_HOST PGPORT=$DB_PORT PGDATABASE=$DB_DATABASE PGUSER=$DB_USERNAME \
  OUT_DIR=$PWD/evidence TENANTS_FILE=wave-1.txt \
  ./rollout.sh wave-1
# then execute: same command with DRY_RUN unset (or false)
```

- **Env contract** — documented in the script header and validated
  up-front (all missing/invalid variables listed, exit 2): required
  `PORT_URL`, `MODULE_TO`, `PGHOST`/`PGDATABASE`/`PGUSER` (+`PGPASSWORD`
  or `~/.pgpass`), `OKAPI_URL` (every real run — rollback exposure needs
  an executable Okapi-side recovery path; dry runs may omit it only in
  `MODE=direct`), `python3` on `PATH`, and `TENANTS_FILE` (one tenant id
  per line) or tenant ids as arguments; optional `MODE` (`okapi` default |
  `direct`), `MODULE_FROM` (the rollback target — use the exact legacy id
  from Phase 0), `OKAPI_TOKEN`, `TENANT_PARAMETERS` (JSON array, validated
  up-front), `LEGACY_URL`+`PRE_PROBES` (per-tenant pre-capture with a
  production-safe, read-only probe subset), `OUT_DIR`, `LEDGER`,
  `MAX_FAILURES`, `CURL_CONNECT_TIMEOUT`/`CURL_MAX_TIME`/
  `CURL_MAX_TIME_INSTALL` (every HTTP call is time-bounded), `DRY_RUN`.
  Tenant ids are trimmed at the edges only — internal whitespace,
  duplicates, and ids longer than 39 characters (the
  `_mod_service_interaction` suffix vs Postgres's 63-byte identifier
  limit) are rejected, never silently repaired.
- **Evidence** — every run gets an immutable directory
  `$OUT_DIR/<wave>/<run-id>/<tenant>/`; an existing run directory is
  refused, so a retry never overwrites the evidence a ledger row already
  references. Every evidence file's sha256 is recorded in the tenant's
  ledger row and in `<tenant>/sha256sums.txt`.
- **Ledger** — `evidence/rollout-ledger.csv` is **append-only**: the
  header (`utc,wave,run_id,tenant,phase,result,detail,evidence_sha256`)
  is written once when the file is created; every run starts with a
  `run-start` row (which doubles as the append probe) and every tenant
  outcome is appended as one row; the file is never truncated. Keep one
  cumulative ledger across all waves — it is the per-tenant audit record:
  which tenants are on which module, with what verified routing state,
  when, and (for failures) the rollback outcome and the exact manual
  follow-up command.
- **Stop conditions** — the run aborts once `MAX_FAILURES` tenants have
  failed (default 1: stop on first failure); a catalog/count/changelog
  verification failure **or a failed rollback** aborts immediately
  regardless of `MAX_FAILURES` — the former is the systemic-defect signal
  of 3.3, the latter leaves a tenant in an unverified routing state that
  must be resolved before anything else. Skipped tenants are named in a
  final `ABORT` ledger row.
- **Automatic rollback** — a failed tenant is rolled back individually
  before the stop check (Phase 6): if the enable never took effect,
  legacy is still authoritative and rollback is *not proceeding* (3.2);
  otherwise, in okapi mode the script performs the **inverse Okapi
  transition** (re-enable `MODULE_FROM`, or disable `MODULE_TO` for a
  fresh tenant — never purge) and **re-verifies routing** via
  `/_/proxy/tenants/<T>/modules`; in direct mode it POSTs the `_tenant`
  2.0 disable to the port. Either way the ledger row records a fully
  resolved manual Phase 6.1 command (the token is referenced as
  `$OKAPI_TOKEN` so the secret never lands in the ledger). A rollback
  that fails or cannot be verified hard-stops the run. Tenants already
  verified stay on 5.0.0 unless the failure indicates a systemic defect
  (see stop conditions).
- **Year-reset timer** — no manual action is needed during rollout: the
  descriptor's `_timer` entry
  (`POST /servint/numberGenerators/resetYearSequences`, every 24 h) is
  registered at enable time; confirm registration per Phase 5.

### Pre-release gate 4.G — the recurring rollout rehearsal lane

The one-off rehearsal evidence (R20, R-CERT) proves the tooling *worked
once*; this gate keeps it true. `docs/migration/harness/rollout-lane.sh`
is a self-contained driver that stands up a disposable rig (its own
Postgres + dev-mode Okapi via `rollout-lane-compose.yml`, plus both module
processes), replays the certified failure-and-recovery scenario against
the **unmodified** `rollout.sh` — a control wave, a forced mid-wave
catalog failure, the automatic verified Okapi rollback, a documented
repair, a resume run into a fresh run directory — and additionally
verifies the failed run's evidence stayed byte-identical and both tenants
end up routed to `MODULE_TO`. Ten named gates; any miss exits non-zero.

- **When**: before every release of this module, and after any change to
  `rollout.sh`, `capture.sh`, the probe lists, or the adoption changelog.
  A release MUST NOT ship on a red or skipped lane run.
- **Owner**: the mod-service-interaction release owner (currently: Taras
  Spashchenko) runs the lane and files the summary line (all-gates-passed
  + evidence path) with the release notes.
- **How**: `cd docs/migration/harness && ./rollout-lane.sh` — prerequisites
  (pre-built legacy + port jars and their module descriptors) and every
  `LANE_*` knob are documented in the script header. Exit 0 = gate passed.

---

## Phase 5 — `_timer` re-registration check (after canary, and once fleet-wide)

The descriptor's `_timer` interface registers
`POST /servint/numberGenerators/resetYearSequences` every 24 h (the
`${current_year}` year-reset mechanism, SI-151). Okapi re-reads timer
registrations from the *installed* descriptor at enable time. Verification
is two-part: the registration exists, and an execution is observed.

**5.1 Registration:**

```bash
curl -s "$OKAPI_URL/_/proxy/tenants/$TENANT/timers" \
  | jq '.[] | select(.routingEntry.pathPattern | test("resetYearSequences"))'
```
Expected: one entry (id `mod-service-interaction_0` — observed in the M7
Okapi 7.0.6 rehearsal, `docs/migration/evidence/r20-rehearsal/`),
`unit: "hour"`, `delay: "24"`. (Older Okapi without the `/timers` API: skip
to 5.2.)

**5.2 Observed execution.** During the canary soak (which covers the 24 h
window), confirm the timer actually fired: the Okapi log shows the timer
call to `/servint/numberGenerators/resetYearSequences`, and/or the module
log shows the request being served. A registered-but-never-firing timer is
a canary abort criterion.

**5.3 Manual trigger — routing caveat.** The route is declared under the
**system `_timer` interface**, not the public `servint` interface, so a
manual POST **through Okapi** with ordinary client credentials is
**expected to answer `404`** — that response does *not* mean the timer is
unregistered, and it is not an abort signal. Do not re-check the
descriptor or roll back on it; registration is proven by 5.1/5.2.

As an explicitly-labeled **diagnostic only** (bypasses Okapi routing —
proves module behavior, not registration), the module-direct call:

```bash
curl -sw '\n%{http_code}\n' -X POST "http://<port-instance>:8081/servint/numberGenerators/resetYearSequences" \
  -H "X-Okapi-Tenant: $TENANT" -H 'Content-Type: application/json'
```
Expected: `200` with `{"currentYear":"2026","sequencesReset":0}` — safe at
any time of year: it resets **only** sequences whose stored year differs
from the current year, so mid-year it is a no-op (a positive count appears
only right after a year boundary). A `5xx` here is a canary abort
criterion.

---

## Phase 6 — Rollback

The cheapest rollback in FOLIO: the adoption pass changed **nothing** the
legacy module reads. Legacy Liquibase state (`tenant_changelog`) is
untouched by the port — that fact is what makes this section short.

**6.1 Re-enable the legacy module for the tenant** (the same command
`rollout.sh` records in a failed tenant's ledger row — fully resolved
except the token, which is referenced as `$OKAPI_TOKEN` so the secret
never lands in evidence; export it first):

```bash
curl --connect-timeout 5 --max-time 600 -sSw '\n%{http_code}\n' -X POST \
  "$OKAPI_URL/_/proxy/tenants/$TENANT/install?deploy=true" \
  -H 'Content-Type: application/json' -H "X-Okapi-Token: $OKAPI_TOKEN" \
  -d '[{"id":"mod-service-interaction-4.4.x","action":"enable"}]' \
  | tee "evidence/$TENANT-rollback.json"
```
(Use the exact legacy id from Phase 0. Okapi treats enabling the older id as
a downgrade; depending on Okapi version you may need
`?deploy=true&purge=false` semantics left at defaults — never purge.)
Expected: `200`. Okapi invokes the legacy module's `_tenant` 1.2 enable,
which reads `tenant_changelog` and finds it exactly as legacy left it.

Then **re-verify routing** — the rollback claim is checked the same way the
cutover claim is:

```bash
curl -sS -H "X-Okapi-Token: $OKAPI_TOKEN" \
  "$OKAPI_URL/_/proxy/tenants/$TENANT/modules" | jq -r '.[].id'
```
Expected: the legacy id listed, the 5.0.0 id absent.

**6.2 Verify legacy serves the tenant (read-only — no getNextNumber):**

```bash
curl -sw '\n%{http_code}\n' "${OK[@]}" "$OKAPI_URL/servint/numberGenerators?perPage=10"
curl -sw '\n%{http_code}\n' "${OK[@]}" "$OKAPI_URL/servint/settings/appSettings?perPage=10"
```
Expected: `200` each. Data written through the port between cutover and
rollback stays valid for legacy — tables, columns, and constraints are
identical (proven at storage level in M4 for dashboards, settings, and
number sequences: port `m4--00004` → legacy continued `m4--00005`).

**6.3 Optional housekeeping** (safe to skip; the rows are invisible to
legacy): drop the port's bookkeeping —
`DROP TABLE ${SCHEMA}.databasechangelog, ${SCHEMA}.databasechangeloglock;`
Doing so means a future re-cutover repeats the MARK_RAN pass from scratch,
which is exactly what you want after any Phase 7 incident.

**6.4 Restore from backup — last resort only** (destroys everything written
after the dump, by either module; only for corruption, never for a routine
rollback):

```bash
# stop module traffic for the tenant first (disable module in Okapi)
"${PSQL[@]}" -c "DROP SCHEMA \"$SCHEMA\" CASCADE;"
pg_restore -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USERNAME" -d "$DB_DATABASE" \
  --exit-on-error "backup/${SCHEMA}.${STAMP}.dump"
# re-run 2.2 and compare with evidence/$TENANT-counts-before.txt
```
Expected: `pg_restore` exits 0; counts match the pre-cutover capture; then
re-enable the legacy module (6.1).

---

## Phase 7 — Liquibase lock and checksum recovery

**Symptom A — enable hangs or fails with a changelog-lock error.**
folio-spring-base 10.x ships
`org.folio.spring.liquibase.LiquibaseMigrationLockService`, which the
framework uses to *detect* an in-progress migration (it polls
`SELECT COUNT(*) = 0 FROM {schema}.databasechangeloglock WHERE locked = false`
and treats a missing lock table as migration-in-progress). It does **not**
clear stale locks — a crash mid-enable can leave `locked = true` forever.
Manual recovery:

```bash
"${PSQL[@]}" -c "SELECT * FROM ${SCHEMA}.databasechangeloglock;"
# if locked=true and lockgranted is old / the instance named in lockedby is gone:
"${PSQL[@]}" -c "DELETE FROM ${SCHEMA}.databasechangeloglock;"
```
Then repeat the enable (3.2). Liquibase recreates the lock row itself.
Expected after retry: `204`/`200` and the Phase 3.3 checks pass.

**Symptom B — checksum-mismatch error on enable** (a tenant was enabled
with an interim 5.0.0 build — see 0.5):

```bash
"${PSQL[@]}" -c "DELETE FROM ${SCHEMA}.databasechangelog;"
```
Then repeat the enable. Safe on adopted **and** fresh tenants: every
changeset re-runs its `tableExists` precondition and MARK_RANs against the
existing tables — no DDL is applied twice.

**Symptom C — legacy-side locks on rollback** (`tenant_changelog_lock`,
`federation_lock`, `system_changelog_lock`): pre-date this migration; see
the root `README.md` "known gotchas" for the legacy recovery procedure.

---

## Appendix A — Container sizing rationale

`launchDescriptor` (descriptors/ModuleDescriptor-template.json):
`Memory: 702293850` bytes ≈ **670 MiB** container limit, with
`JAVA_OPTIONS=-XX:MaxRAMPercentage=66.0` → max heap ≈ **442 MiB**, leaving
~228 MiB for Metaspace, code cache, thread stacks, and direct buffers.

Why these numbers: they are byte-identical to `mod-consortia-keycloak`'s
launch descriptor — the platform-standard sizing for folio-spring/Boot 4/
Java 21 modules of this class. The workload justifies the class: a small
CRUD surface, no Kafka, listing pages capped at `perPage ≤ 100`, and the
heaviest allocation (widget-definition fetches) bounded by dashboard counts.
The legacy Grails module ran 1 GiB at 55% (≈ 563 MiB heap) — the Grails/GORM
baseline is simply heavier.

**Honest limit of this claim (review F-12):** no production-load test has
been run against the port. The canary soak (Phase 3.6) is the empirical
sizing gate — watch container RSS and GC time there; sustained RSS > 85–90%
of the limit or any OOMKilled is an abort criterion, and the remedy is
raising `Memory` in the descriptor before batch rollout, not hoping.

## Appendix B — Descriptor parity

`descriptors/ModuleDescriptor-template.json` vs legacy
`service/src/main/okapi/ModuleDescriptor-template.json`, machine-diffed:

| Surface | Result |
|---|---|
| `provides` | `servint 4.4` (43 handlers), `dashboard 1.0`, `_timer 1.0` identical; `_tenant` deliberately upgraded 1.2 → **2.0** (D-17, ADR-012) |
| Handler entries | Identical methods, pathPatterns, permissionsRequired/Desired, modulePermissions; same `resetYearSequences` timer (24 h) |
| `permissionSets` (63) | Content-identical |
| `requires` / `optional` | Identical (`okapi 1.9`; optional `dashboard 1.0`) |
| Template tokens | Maven `@artifactId@`/`@version@` vs Gradle `${info.app.*}` — resolve to the same module id shape |
| `launchDescriptor` | Port: folio-spring conventions — port 8081, `DB_*` env, 670 MiB/66% (Appendix A); legacy: 8080, 1 GiB, 55%. Deployment-level only |

## Appendix C — Evidence base and its limits

Proven by repo artifacts (cite-able): adoption MARK_RAN behavior and
business-schema stability (M4 + review C2 body); fresh-DDL parity modulo
D-16 (review C3 + R7 fix); seeding idempotence (M4); storage-level rollback
compatibility (M4 rollback probe); `_tenant` 2.0 route behavior
(`TenantEnableIT`); generator/dashboard/refdata/settings/attestation wire
behavior (`NumberGeneratorParityIT`, `NumberGeneratorConcurrencyIT`,
`DashboardParityIT`, `RefdataSettingsParityIT`, `AttestationParityIT`,
`WidgetFederationParityIT`); registered deviations D-1..D-19
(`wire-compat-deviations.md`).

Not proven by the repo — closed only by executing this runbook on the
rehearsal rig: Okapi-orchestrated enable/rollback end-to-end (review C10),
`_timer` firing through a real Okapi, production load sizing (Appendix A),
and the batch-rollout mechanics of Phase 4.
