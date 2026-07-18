# R-CERT evidence forensics — recompute of every documented hash (M8)

Performed 2026-07-22 at cert HEAD `63c8b2f` (spec/src state; the R-CERT
commits add only `docs/` evidence). Every recomputation ran against the
committed files in the working tree.

## 1. r18 wire-parity bundle (`evidence/r18/`)

| Check | Result |
|---|---|
| Body hashes: sha256 of every committed probe body vs its `manifest.json` `body_sha256` | **84/84 match** (28 legacy + 28 port + 28 port-adopt-r18l), `failed_probes: []` in all three manifests |
| Harness-input pin: `probe_list.sha256` in all three manifests | all three = `fe32cb3b…` — the r18-era `probes.tsv`, matching the provenance.md table (the CURRENT `probes.tsv` is `48ee6edb…` after the R22 rows — an evolution, not a mismatch) |
| Parity verdict recompute: `diff-runs.sh legacy port-adopt-r18l` re-run against the committed captures | exit 0; output content-identical to the committed `diff-runs.txt` — the only deltas are path spelling (relative vs absolute invocation) and the manually appended `diff-runs exit code: 0` line. Every verdict row and category count identical |

## 2. r20 rehearsal bundle (`evidence/r20-rehearsal/`) — post-R27 state

| File | Documented sha256 | Recomputed |
|---|---|---|
| `proxy-logs/proxy-legacy.jsonl` (1 record redacted post-run) | `cfe83d7e…697d88` | **match** |
| `proxy-logs/proxy-port.jsonl` | `587e10f2…0846bf` | **match** |
| `proxy.py` (R27-hardened) | `9e661e19…f991c3` | **match** |

## 3. Semantic corpus (`evidence/semantic/`)

| File | Documented sha256 | Recomputed |
|---|---|---|
| `structural-validation.json` (R27 baseline) | `f3bf87fe…07ad12` | **match** |
| `semantic-verdicts-full.json` (R27 baseline) | `37195120…d86e28` | **match** |

R-CERT recapture (`semantic/rcert/`): fresh `sdd validate --semantic --branch
main-final` at cert HEAD — 165 files 0/0 `pass: true`, gate PASS, 245/245
findings PASS (all cache — spec tree unchanged since R28); sha256 of both new
files in `semantic/README.md`.

## 4. Descriptors — the deterministic artifact

| Artifact | Result |
|---|---|
| `target/ModuleDescriptor.json` rebuilt this round from HEAD | sha256 `6c14c195…` — **byte-identical to the r20 `descriptors.txt` pin** |
| legacy `service/build/resources/main/okapi/ModuleDescriptor.json` | sha256 `2f94a80e…` — **matches the r20 pin** |

## 5. Rebuilt binaries — pinned, not reconstructed (the honest F-22 statement)

| Artifact | This round |
|---|---|
| Port jar `target/mod-service-interaction-5.0.0-SNAPSHOT.jar` | sha256 `9f749b0db76cb5d41c0ee494c8cb5532a101510accdf0b8d5ab5b727ca568c53` |
| Port image `mod-service-interaction:rcert` | Id `sha256:8782e828b3dabfb562e6603fa182e400759c9f98456ea2c15bb5fd1b51475375` |

These hashes differ from the r18-recorded jar/image hashes **by construction**
(JARs are timestamp-nondeterministic and the tree has moved: R22–R29 landed
since). That is exactly the provenance model the r18/r20 bundles document:
recorded hashes identify what ran; re-verification is rebuild + re-run — which
is what this R-CERT round did (fresh jar, fresh image, full harness + Okapi
rehearsal against them).

## 6. Credential sweep

`grep` over every retained evidence file for the rehearsal operator token and
for cleartext `Authorization`/`X-Okapi-Token`/`Cookie` values in persisted
captures: **zero hits**. The only credential-shaped value ever committed
(r20 `proxy-legacy.jsonl` record 1) remains redacted as
`[REDACTED sha256:ceec12762e66397b]`.
