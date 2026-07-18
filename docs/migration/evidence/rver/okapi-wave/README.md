# R-VER gate 9 — real-Okapi failure/rollback/resume wave (corrected artifacts)

One end-to-end run of the recurring rollout rehearsal lane
(`docs/migration/harness/rollout-lane.sh`, runbook pre-release gate 4.G) at
the M9 closing HEAD, against the artifacts as corrected by M9: freshly built
port jar `9611a5bd…` behind a real dev-mode Okapi (`folioorg/okapi:latest`),
legacy jar `abb37211…`, disposable lane Postgres. The lane wraps the
**unmodified** `rollout.sh`. Run date: 2026-07-23. **ALL 10 lane gates
passed; exit 0** (`lane-driver-transcript.txt`).

The wave narrative, from `rollout-evidence/rollout-ledger.csv`:

| Wave | Tenant | Outcome |
|---|---|---|
| lane1 (control) | `lanec` | `complete` **PASS** — Okapi routing verified to 5.0.0, catalog + row counts unchanged, changelog EXECUTED=0 / MARK_RAN=14, smoke 2/2 via Okapi |
| lane2 (forced failure) | `lanebad` (3 widget tables dropped pre-wave) | `post-verify-catalog` **FAIL** → **ABORT**; automatic Okapi rollback re-verified (routing lists 4.4.0 again), legacy data untouched |
| lane2 (resume, after documented repair) | `lanebad` | `complete` **PASS** in a **fresh run directory** |

Immutability: all 15 evidence files of the failed run re-hashed byte-identical
after the resume (`immutability-before.txt` / `immutability-after.txt`).
Final routing: both tenants list `mod-service-interaction-5.0.0-SNAPSHOT`.

Credential hygiene: the operator token is a lane-generated JWT-shaped dummy,
referenced in the ledger only as the `$OKAPI_TOKEN` shell expansion — no token
value in any retained file (swept, see `../forensics.md` §6).

Contents: `rollout-evidence/` (ledger + both waves' run dirs, verbatim),
`rollout-lane1.log` / `rollout-lane2-fail.log` / `rollout-lane2-resume.log`
(per-wave `rollout.sh` transcripts), `immutability-*.txt`,
`lane-driver-transcript.txt` (the ten lane gates). Module boot logs
(`okapi.log`, `legacy.log`, `port.log`) were not retained — the ledger and
transcripts carry the assertions.
