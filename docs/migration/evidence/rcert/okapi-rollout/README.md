# R-CERT — real-Okapi re-rehearsal of the corrected rollout.sh (M8, review №3 merge-gate item 1)

Empirical validation of the R23-rewritten `docs/migration/harness/rollout.sh`
against a **real Okapi** (7.0.6, dev mode) — the leg review №3 required after
F-27 (rollout PASS without Okapi cutover), F-28 (retry destroyed evidence),
F-29 (failure handling), F-30 (tenant normalization) and F-35 (non-executable
rollback command) were fixed tool-side. The scenario exercises, in one
connected narrative: a **control wave** with verified routing, a **forced
mid-wave failure** caught by the adoption catalog gate, an **automatic
authenticated rollback** with inverse-routing re-verify, a **repair**, and a
**resume run** into a fresh run directory with the prior run's evidence
provably intact.

Run date: 2026-07-22. Script, harness and probe inputs UNMODIFIED from the
committed tree.

## Rig

| Component | Detail |
|---|---|
| Okapi | `folioorg/okapi:latest` = **7.0.6**, digest `sha256:0a0d3c62…9fd7da`, Id `48976ccddca3` (same image as the R20 rehearsal), container `okapi-rcert`, dev (in-memory) mode, host network, :9130 |
| Legacy module | `mod-service-interaction-4.4.0-SNAPSHOT` (`_tenant` 1.2), long-running host java process :8080 (jar sha256 `abb37211…9b4c57`), MD sha256 `2f94a80e…` (unchanged since R20) |
| Port module | `mod-service-interaction-5.0.0-SNAPSHOT` (`_tenant` 2.0), container `rcert-port` from image **`mod-service-interaction:rcert`** (Id `8782e828b3da`) — **rebuilt this round from cert HEAD** (jar sha256 `9f749b0d…`; the stale `r18` image was NOT used), :8081. MD `target/ModuleDescriptor.json` sha256 `6c14c195…` — byte-identical to the R20 pin (the descriptor is the deterministic artifact) |
| Database | `testing_pg` (postgres:18, host :54321), db `okapi_modules_test` |
| Wire taps | the **R27-hardened** `proxy.py` (from `evidence/r20-rehearsal/`, sha256 `9e661e19…`): :18080 → legacy, :18081 → port; Okapi discovery points at the taps, so every module-bound call crossed them |
| Auth | every Okapi call carried `X-Okapi-Token` (JWT-shaped dummy — Okapi dev parses supplied tokens and rejects bare words like `DUMMY` with 400). The token value appears in **no file** of this bundle; ledger/manual commands reference `$OKAPI_TOKEN` (F-34/F-35 hygiene) |
| psql | no psql client on the host: `psql` on PATH was a 6-line shim forwarding to the client inside `testing_pg` (host :54321 = in-container :5432). Environmental provisioning only — the harness itself is unmodified |

## Scenario and outcomes

Four tenants created in Okapi and legacy-enabled through
`install?deploy=true` with `tenantParameters=loadReference=true,loadSample=true`
(adoption pre-state: 37-table seeded schemas). `rcw2bad` then **deliberately
corrupted**: `widget_instance`, `widget_definition`, `widget_type` dropped
(34 tables) — a realistic half-broken adopted tenant.

| Run | Exit | What it proves |
|---|---|---|
| `rollout.sh rcw1 rcw1a rcw1b` (control) | 0 | The checked cutover: Okapi install 200 → routing verified (`/_/proxy/tenants/<T>/modules` lists 5.0.0) → catalog byte-unchanged → `EXECUTED=0 MARK_RAN=14` → smoke **through Okapi** 2/2 → ledger `complete` PASS rows carrying the observed routing state. Pre-capture (2 read-only probes vs legacy) retained per tenant |
| `rollout.sh rcw2 rcw2ok rcw2bad` (forced failure) | 1 | `rcw2ok` passes mid-wave. `rcw2bad`: enable 200 + routing verified, then **`post-verify-catalog` FAIL** — `catalog.diff` (kept, hashed into the ledger) pins exactly the 20 column rows of the three port-recreated widget tables. **Automatic rollback**: Okapi install of `module_from` → 200 + **routing re-verified** (Okapi lists 4.4.0 again; wire capture `05-legacy.body.json` shows the inverse transition `{module_to: 4.4.0, module_from: 5.0.0}`). HARD_STOP aborts the run regardless of `MAX_FAILURES` |
| `rollout.sh rcw2 rcw2bad` (resume) | 0 | Fresh run-id `20260722T190736Z-1336414`; after the documented repair (drop the failed attempt's `databasechangelog{,lock}`), the tenant adopts cleanly: 14/14 MARK_RAN, catalog unchanged, routing verified, `complete` PASS |

**Evidence immutability (F-28):** all 28 files of the failed run
(`rcw2/20260722T190648Z-1334794/`) were sha256-hashed before and after the
resume run — **byte-identical**. The retry landed in its own directory; prior
evidence untouched, `catalog.diff` never deleted.

**Wire truth (D-26):** all five port-bound `/_/tenant` bodies are the 2.0
upgrade shape `{module_to, module_from, purge:false, parameters}`; the four
legacy enables are the 1.2 shape (no purge field); the rollback body is the
inverse transition. Raw bodies + headers in `tenant-calls-*/`, complete JSONL
logs in `proxy-logs/` — captured by the R27-hardened proxy (zero
sensitive-header values persisted; Okapi dev forwards no auth headers to
modules).

**Final state:** all four tenants routed to 5.0.0; `_timer` registered
(`mod-service-interaction_0` → `POST /servint/numberGenerators/resetYearSequences`,
hour/24).

## Bundle layout

```
okapi-rollout/
  README.md                  # this file
  transcript.md              # every setup/injection/repair step + wave summary + verifications
  pre-probes.tsv             # the 2-row read-only probe subset given to PRE_PROBES
  logs/                      # full rollout.sh stdout of all three runs
  rollout-evidence/          # the script's own OUT_DIR — IMMUTABLE run dirs
    rollout-ledger.csv       # cumulative append-only 8-col ledger (9 rows, 3 runs)
    rcw1/20260722T190634Z-1333720/{rcw1a,rcw1b}/       # control wave evidence
    rcw2/20260722T190648Z-1334794/{rcw2ok,rcw2bad}/    # forced-failure run (intact)
    rcw2/20260722T190736Z-1336414/rcw2bad/             # resume run
    (per tenant: pre/ capture, catalog/counts before+after, catalog.diff,
     okapi-modules.json, smoke bodies, sha256sums.txt)
  tenant-calls-legacy/       # raw /_/tenant bodies+headers seen by the legacy tap (5 calls)
  tenant-calls-port/         # raw /_/tenant bodies+headers seen by the port tap (5 calls)
  proxy-logs/                # complete JSONL request logs of both taps
```
