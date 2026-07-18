# R23 acceptance probes — corrected `rollout.sh` (DRY_RUN / config-gate matrix)

Run 2026-07-22 against `docs/migration/harness/rollout.sh` as committed in
the R23 workstream (remediation-plan-3.md §R23; review №3 findings
F-27/F-28/F-29/F-30/F-35). Static checks the same day: `bash -n` clean;
shellcheck clean at **default (style) severity** via
`docker run --rm -v "$PWD:/mnt:ro" koalaman/shellcheck:stable /mnt/rollout.sh`
(exit 0, no output; local shellcheck not installed on the rig host).

Full empirical validation (real-Okapi wave with routing verification,
forced failure, retry-new-run-dir, rollback verify, resume) is deferred to
R-CERT per the plan.

| Probe | Configuration | Expect | Got |
|---|---|---|---|
| p1-malformed | `DRY_RUN=true MODE=direct MODULE_TO='mod"bad' TENANT_PARAMETERS=not-json` (the review's F-29 case) | exit 2, both errors listed | exit 2 — `MODULE_TO 'mod"bad' is not a valid module id …; TENANT_PARAMETERS is not valid JSON …` |
| p2-okapi-dry | well-formed okapi-mode dry run (OKAPI_URL, MODULE_FROM, TENANT_PARAMETERS array) | exit 0; renders Okapi install POST with `tenantParameters=loadReference%3Dtrue`, routing verification against `/_/proxy/tenants/<T>/modules`, through-Okapi smoke, `complete` PASS row carrying routing state, `--max-time 600` on the install call | exit 0, all rendered (see p2-okapi-dry.out) |
| p3-direct-dry | well-formed `MODE=direct` dry run, no OKAPI_URL | exit 0; banner + ledger rows say `verified-direct`, never `complete` | exit 0, `verified-direct` (see p3-direct-dry.out) |
| p4-okapi-nourl | okapi mode (default), OKAPI_URL unset, dry | exit 2 (mode cannot render without Okapi) | exit 2 |
| p5-real-nourl | `DRY_RUN=false MODE=direct`, OKAPI_URL unset | exit 2 before any side effect (F-35: real runs have rollback exposure) | exit 2, nothing created |
| p6-internal-ws | wave file containing `row 3ctl` | exit 2 — internal whitespace rejected, never squashed (F-30) | exit 2 |
| p7-trim | wave file `"  rowok  "` + comment + blank line | exit 0 — outer whitespace trimmed, tenant is `rowok` | exit 0 |
| p8-dup | wave file listing `rowok` twice | exit 2 — duplicate rejected | exit 2 |
| p9a-len40 | 40×`a` tenant argument | exit 2 — `len(tenant)+24 > 63` (schema-name overflow) | exit 2 |
| p9b-len39 | 39×`a` tenant argument | exit 0 — boundary accepted | exit 0 |

Raw outputs are the `p*.out` files here (stdout+stderr, verbatim); the
`wave-*.txt` files are the tenant-list fixtures p6/p7/p8 consumed. Dry-run
ledger lines inside the outputs show the 8-column schema
(`utc,wave,run_id,tenant,phase,result,detail,evidence_sha256`) and the
run-id-scoped evidence paths (`evidence/<wave>/<run-id>/<tenant>/…`, F-28).
