# R-CERT — M8 certification round evidence (remediation-plan-3 §R-CERT)

The certification round after workstreams R22–R29 landed: everything rebuilt
from cert HEAD and re-proven empirically. Run date: 2026-07-22. Cert HEAD
`63c8b2f` (the last commit touching `specs/` or `src/`; the R-CERT commits add
only `docs/` evidence).

## Build identity (rebuilt this round — the stale `r18` image was NOT used)

| Artifact | Value |
|---|---|
| `mvn -B verify` (full suite) | BUILD SUCCESS — 3 unit + **95 integration tests, 0 failures** |
| Port jar | `target/mod-service-interaction-5.0.0-SNAPSHOT.jar` sha256 `9f749b0db76cb5d41c0ee494c8cb5532a101510accdf0b8d5ab5b727ca568c53` (`mvn -B -DskipTests clean package`) |
| Port image | `mod-service-interaction:rcert`, Id `sha256:8782e828b3dabfb562e6603fa182e400759c9f98456ea2c15bb5fd1b51475375` (checked-in `Dockerfile`) |
| `target/ModuleDescriptor.json` | sha256 `6c14c195…` — byte-identical to the r20 pin (deterministic artifact) |
| Legacy side | the standing rig: jar sha256 `abb37211…9b4c57`, JDK 17, :8080; `testing_pg` postgres:18 :54321 |

## What was re-proven, where

| Leg | Evidence | Outcome |
|---|---|---|
| Semantic gate at cert HEAD | `../semantic/rcert/` | structural 165 files 0/0 pass; gate PASS, **245/245 findings PASS** (all cache — spec tree unchanged since R28) |
| Unmodified harness, both sides, incl. the R22 compound probes | `harness/` | legacy tenant `rcl`: populate OK + **35/35** pins; port fresh-DDL tenant `rcp`: populate OK + **35/35**; adoption enable of `rcl` on the port (Okapi-truth body, `purge:false`): 204 + **35/35**; `diff-runs.sh legacy vs port-adopt-rcl`: **exit 0 — byte-equal 15, sorted-equal 8 (D-4 order only), allowed 12 (all registered deviations incl. D-30/D-32), diverged 0** |
| R25 tenant-lifecycle contract over the wire | `lifecycle/` | fresh enable 204; **flagless disable, `{}` body, and explicit-`null` purge each → 400 `purge.not.explicit` with schema present and changelog frozen** (zero side effects); explicit `{module_from, purge:false}` disable → 204 side-effect-free; re-enable 204; explicit `purge:true` → 204 schema dropped |
| Real-Okapi rollout re-rehearsal (control wave / forced failure / verified rollback / resume) | `okapi-rollout/` | see its README — control wave 2/2 `complete` with verified routing; forced catalog-gate failure with automatic authenticated rollback + inverse routing re-verify; resume in a fresh run dir; prior run's 28 evidence files byte-identical after the retry; token value in no file |
| Evidence forensics | `forensics.md` | every documented hash recomputed and held (84/84 r18 bodies, r20 post-R27 files, semantic baselines, descriptor pins); parity verdict recomputable; credential sweep clean |

## Layout

```
rcert/
  README.md          # this file
  forensics.md       # recompute of every documented evidence hash
  harness/           # tenant-enable bodies, populate + capture evidence (3 runs × 35 probes), diff-runs.txt
  lifecycle/         # R25 wire re-verify: per-step response bodies + transcript.txt (status + schema state per step)
  okapi-rollout/     # the real-Okapi rollout.sh re-rehearsal bundle (own README)
```

Related bundles updated this round: `../semantic/rcert/` (corpus recapture,
documented in `../semantic/README.md`).
