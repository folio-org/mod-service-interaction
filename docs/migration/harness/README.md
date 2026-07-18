# Cutover rehearsal harness

Evidence-grade wire-parity harness (M4/M5 method, rewritten for review finding
F-11): populate a tenant through the legacy REST API, capture the wire-visible
state of both modules on the same database, and diff the two capture runs
against the registered deviation dossier. Every request asserts its expected
status; every run leaves an auditable `manifest.json`; any unexplained
difference exits non-zero.

## Scripts

| Script | Purpose |
|---|---|
| `populate.sh` | Fill tenant `$TENANT` with rows in every port-owned table via REST. Run **once** per fresh tenant, normally against legacy (`SIDE=legacy`, default). Asserts every status (incl. the deliberate D-2 legacy 500) and every extracted id; evidence in `$OUT_DIR/populate-<side>/`. |
| `capture.sh <legacy\|port>` | Run every probe in `probes.tsv`, assert each response status for that side, write normalized bodies + `manifest.json` to `$OUT_DIR/<side>/`. Exit 1 on any status mismatch, naming the probe. |
| `diff-runs.sh DIR_A DIR_B` | Compare two capture runs by manifest: per-probe `byte-equal` / `sorted-equal` (order-only, D-4) / `allowed` (allowlisted registered deviation) / `DIVERGED`. Exit 1 on any non-allowlisted divergence. |
| `probes.tsv` | Data-driven probe list: the 13 M4 endpoints + the review's F-04 (filter/perPage/sort) and F-14 (malformed JSON, invalid boolean, missing tenant header, garbage stats/perPage leniency, duplicate-code integrity conflict) probes. Append rows to extend the matrix — no script change needed. |
| `deviation-allowlist.tsv` | Probes allowed to diverge, each citing a registered deviation D-1..D-25 (`../wire-compat-deviations.md`). |
| `rollout.sh` | Batch-rollout executor (cutover runbook Phase 4): checked Okapi cutover per tenant with append-only ledger, immutable evidence, and automatic verified rollback. Env contract in the script header. |
| `rollout-lane.sh` + `rollout-lane-compose.yml` + `rollout-lane-preprobes.tsv` | Recurring rollout rehearsal lane (runbook pre-release gate 4.G): stands up a disposable rig (own Postgres + dev-mode Okapi + both modules), replays control wave / forced failure / rollback / resume against the unmodified `rollout.sh`, checks evidence immutability and final routing; non-zero exit on any of its ten gates. |

## Prerequisites

- `bash`, `curl`, `jq` >= 1.6 (`walk` builtin), `sha256sum`.
- Postgres from `tools/testing`: `cd tools/testing && docker compose up -d`
  (Postgres 18 on host port **54321**, DB `okapi_modules`, user/password
  `folio_admin`/`folio_admin` — created by `init.sql`).
- Legacy module buildable (`service/`, Java 17/Gradle); port jar built at
  `target/mod-service-interaction-5.0.0-SNAPSHOT.jar` (Java 21/Maven).

## Environment variables (all optional)

| Variable | Default | Meaning |
|---|---|---|
| `LEGACY_URL` | `http://localhost:8080` | Base URL of the booted legacy module |
| `PORT_URL` | `http://localhost:8081` | Base URL of the booted port |
| `TENANT` | `m4proof` | Tenant id (schema `<tenant>_mod_service_interaction`) |
| `OUT_DIR` | `$PWD/captures` | Work/evidence directory for all scripts |
| `USER_A` / `USER_B` | `11111111-…` / `22222222-…` | The two rehearsal user ids |
| `PROBES` | `probes.tsv` beside the scripts | Probe list for `capture.sh` |
| `SIDE` | `legacy` | `populate.sh` only: which side to populate |

## Rehearsal flow (exact commands)

```bash
export OUT_DIR=$HOME/msi-rehearsal          # anywhere writable
cd docs/migration/harness

# 1. database
( cd ../../../tools/testing && docker compose up -d )

# 2. boot legacy (Grails, port 8080) against the compose DB
( cd ../../../service && OKAPI_SERVICE_HOST=localhost OKAPI_SERVICE_PORT=9130 \
    ./gradlew bootRun -Ddb.host=localhost -Ddb.port=54321 -Ddb.database=okapi_modules )
# (if your Gradle does not forward -D system properties to bootRun, export them
#  via GRAILS_OPTS/JAVA_OPTS instead)

# 3. enable the tenant on legacy (_tenant 1.2), with reference + sample data
curl -sw '\n%{http_code}\n' -X POST "http://localhost:8080/_/tenant" \
  -H 'X-Okapi-Tenant: m4proof' -H 'X-Okapi-Token: DUMMY' -H 'Content-Type: application/json' \
  -d '{"parameters":[{"key":"loadReference","value":"true"},{"key":"loadSample","value":"true"}]}'
# expect: 2xx; the schema m4proof_mod_service_interaction now exists

# 4. populate (once, legacy only) and capture the legacy baseline
./populate.sh
./capture.sh legacy

# 5. stop legacy (Ctrl-C the bootRun), then boot the port on the SAME database
DB_HOST=localhost DB_PORT=54321 DB_DATABASE=okapi_modules \
DB_USERNAME=folio_admin DB_PASSWORD=folio_admin \
  java -jar ../../../target/mod-service-interaction-5.0.0-SNAPSHOT.jar &

# 6. enable the tenant on the port (_tenant 2.0 body shape; see D-15/D-17)
curl -sw '\n%{http_code}\n' -X POST "http://localhost:8081/_/tenant" \
  -H 'X-Okapi-Tenant: m4proof' -H 'x-okapi-url: http://localhost:9130' \
  -H 'Content-Type: application/json' \
  -d '{"module_from":"mod-service-interaction-4.4.0","module_to":"mod-service-interaction-5.0.0","parameters":[{"key":"loadReference","value":"true"},{"key":"loadSample","value":"true"}]}'
# expect: 204 (synchronous completion; adoption changesets MARK_RAN)

# 7. capture the port and diff the two runs
./capture.sh port
./diff-runs.sh "$OUT_DIR/legacy" "$OUT_DIR/port"
echo "exit: $?"          # 0 = parity modulo registered deviations
```

Expected `diff-runs.sh` outcome on the M4 dataset: the 13 M4 probes
byte-equal or sorted-equal (D-4) except `my-widgets-A` (`allowed`, D-1); the
F-04 probes equal once the R5 listing engine is in place; the F-14 probes are
pinned on the port side by R9 (`ErrorEnvelopeMatrixIT`, dossier D-22..D-25)
and allowlisted where the mapping deliberately differs from legacy —
`f14-malformed-json` (D-22), `f14-missing-tenant` (D-23), `f14-duplicate-code`
(D-24), `f14-invalid-boolean` (D-28: legacy 500, port coerce-to-false 200 —
R13 falsified the old "legacy-identical" assumption); the true leniency
probes (`f14-stats-notabool`, `f14-perpage-nonnumeric`) are legacy-identical
and must not diverge. The R16 `f21-*` probes pin the directed parser rows
(D-29..D-31 where sides diverge; `f21-empty-rhs` is parity by construction).

## The manifest

Each capture run writes `manifest.json`:

```json
{
  "side": "legacy", "base_url": "…", "tenant": "m4proof",
  "captured_at": "2026-07-19T…Z",
  "probe_list": {"file": "probes.tsv", "sha256": "…"},
  "harness_git_rev": "…", "probe_count": 20, "failed_probes": [],
  "probes": [ {
      "id": "numgen",
      "request": {"method": "GET", "path": "/servint/numberGenerators?…", "user": "A", "body": null},
      "expected_status": 200, "actual_status": 200,
      "response_file": "numgen.json", "body_sha256": "…",
      "normalization": "jq-S", "result": "pass"
  } ]
}
```

- `expected_status` is the side-specific pin from `probes.tsv`
  (`"unpinned"` for `*` rows); `result` is `pass` / `fail` / `recorded`.
- `response_file` bodies are `jq -S`-normalized (key-sorted; array order
  preserved so D-4 remains observable); `normalization: "raw"` marks
  non-JSON/empty bodies kept verbatim. `body_sha256` is over that file.
- A reviewer can re-verify any row: replay `request` against `base_url`,
  normalize, compare hashes.

## Probe list format

Tab-separated: `id  method  user  expect_legacy  expect_port  path  body`
(`#` comments; `user` = `A`/`B`/`notenant`; `body` = `-` or a literal —
possibly deliberately malformed — JSON string; `{DASH}` in a path resolves to
the "Migration Board" dashboard id; `*` = status not pinned yet — recorded,
warned, and still diffed across runs). Pin `*` rows with the statuses observed
in the first legacy baseline capture.

## Diffing and the allowlist

`diff-runs.sh` fails (exit 1) on any probe that is missing from one manifest,
differs in status, or differs in body beyond array order — unless
`deviation-allowlist.tsv` lists it with the deviation ids (D-1..D-31) that
explain it. Keep the allowlist minimal and dossier-backed: an unexplained
divergence must fail the rehearsal, that is the point (review F-11).
