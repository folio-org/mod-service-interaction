# R-VER evidence forensics — recompute of every documented hash (M9)

Performed 2026-07-23 at the M9 closing HEAD (spec/src state; the R-VER commits
add only `docs/` evidence plus the two R-VER-traced harness fixes noted in the
bundle README). Every recomputation ran against the committed files in the
working tree.

## 1. r18 wire-parity bundle (`evidence/r18/`)

| Check | Result |
|---|---|
| Body hashes: sha256 of every committed probe body vs its `manifest.json` `body_sha256` | **84/84 match** (28 legacy + 28 port + 28 port-adopt-r18l), `failed_probes: []` in all three manifests |

## 2. r20 rehearsal bundle (`evidence/r20-rehearsal/`)

| File | Documented sha256 | Recomputed |
|---|---|---|
| `proxy-logs/proxy-legacy.jsonl` | `cfe83d7e…697d88` | **match** |
| `proxy-logs/proxy-port.jsonl` | `587e10f2…0846bf` | **match** |
| `proxy.py` | `9e661e19…f991c3` (R27 state) | now `a63a0ea2…283bc8` — the **R33 hardening** (commit `5f35675`: `proxy-authorization`/`*-authorization` headers, query-credential redaction, self-test) supersedes the R27 pin. Documented evolution, not a mismatch; the R33 self-test re-run this round is gate 4. |

## 3. Semantic corpus (`evidence/semantic/`)

| File | Documented sha256 | Recomputed |
|---|---|---|
| `structural-validation.json` (R27 baseline) | `f3bf87fe…07ad12` | **match** |
| `semantic-verdicts-full.json` (R27 baseline) | `37195120…d86e28` | **match** |
| `rcert/structural-validation.json` | `970b45d7…95e095` | **match** |
| `rcert/semantic-verdicts-full.json` | `29edbf12…32a753` | **match** |

R-VER recapture (`semantic/rver/`): fresh `sdd validate --semantic --branch
main-final` at the closing HEAD — 166 files `pass: true` (one advisory
warning: `adr-has-architecture-context` on the new ADR-013, which references
REQ-016 rather than an architecture component); gate PASS, **249/249 findings
PASS, 0 escalation-eligible — 230 cache + 19 fresh `model` verdicts**. The 19
fresh verdicts are exactly the M9-touched pairs (REQ-022 listing-grammar ACs +
scenarios, REQ-020 tenant-lifecycle AC6 + the new purge-discriminator
scenario, and their API backings): their cache entries were evicted before the
run so the M9 spec changes were live-judged, not replayed. sha256 of both new
files in `semantic/README.md`.

## 4. Descriptors — the deterministic artifact

| Artifact | Result |
|---|---|
| `target/ModuleDescriptor.json` rebuilt this round from HEAD | sha256 `6c14c195…` — **byte-identical to the r20/R-CERT pin** |
| legacy `service/build/resources/main/okapi/ModuleDescriptor.json` | sha256 `2f94a80e…` — **matches the r20 pin** |

## 5. Rebuilt binaries — pinned, not reconstructed

| Artifact | This round |
|---|---|
| Port jar `target/mod-service-interaction-5.0.0-SNAPSHOT.jar` | sha256 `9611a5bdeb771b06fb70f38a3aea7fcc487007baf4317c2d4ec28962e92f5eeb` |
| Port image `mod-service-interaction:rver` | Id `sha256:a358168ca5ea0f0890cc6d3ccf113da78101c37a21c899cb194451622220306b` |

These differ from the R-CERT-recorded jar/image hashes **by construction**
(timestamp-nondeterministic JARs; M9 R30–R37 landed since). Recorded hashes
identify what ran; re-verification is rebuild + re-run — which is what this
round did (fresh jar, fresh image, all nine gates against them).

## 6. This round's own bundle

| Check | Result |
|---|---|
| `rver/harness/` capture bodies vs their three `manifest.json` files | **132/132 match** (44 legacy + 44 port + 44 port-adopt-rvl), `failed_probes: []` in all three |
| Parity verdict | `diff-runs.txt`: byte-equal 21, sorted-equal 9 (D-4), allowed 14 (all registered), **diverged 0, exit 0** |
| Credential sweep over `evidence/` (operator-token value, cleartext `Authorization`/`X-Okapi-Token`/`Cookie` in persisted captures) | **zero hits**; the rollout ledger references the token only as the `$OKAPI_TOKEN` shell expansion; r20's single redacted record remains `[REDACTED sha256:ceec12762e66397b]` |
