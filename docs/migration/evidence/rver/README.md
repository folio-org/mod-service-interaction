# R-VER — M9 targeted verification round (remediation-plan-4 §R-VER)

The reviewer of BMAD Party review №4 waived a fifth wholesale review
conditional on **nine named targeted gates**, each reproduced at the M9
closing HEAD with no unrelated behavior changes riding along. This bundle is
those nine gates. Run date: 2026-07-23. Closing HEAD spec/src state:
`66c5435` (R37) — the R-VER commits add `docs/` evidence plus exactly two
R-VER-traced harness fixes, listed at the end of this file.

## Build identity (rebuilt this round — no stale artifact reused)

| Artifact | Value |
|---|---|
| `mvn -B clean verify` (full suite) | BUILD SUCCESS — **7 unit + 106 integration tests, 0 failures** (`build.txt`) |
| Port jar | `target/mod-service-interaction-5.0.0-SNAPSHOT.jar` sha256 `9611a5bdeb771b06fb70f38a3aea7fcc487007baf4317c2d4ec28962e92f5eeb` |
| Port image | `mod-service-interaction:rver`, Id `sha256:a358168ca5ea0f0890cc6d3ccf113da78101c37a21c899cb194451622220306b` (checked-in `Dockerfile`) |
| `target/ModuleDescriptor.json` | sha256 `6c14c195…` — byte-identical to the r20/R-CERT pin (deterministic artifact) |
| Legacy side | the standing rig: jar sha256 `abb37211…9b4c57`, JDK 17, :8080; `testing_pg` postgres:18 :54321 |

## The nine gates

| # | Gate | Evidence | Outcome |
|---|---|---|---|
| 1 | Targeted purge matrix over the wire (R30's adversarial shapes) | `purge-matrix/` | **19/19 PASS, exit 0** — enable 204; F-32 flagless trio and all 8 F-37 shapes (coercible scalars, both duplicate orders, `module_to` variant, structured value) → 400 `purge.not.explicit`; nested-purge control 400; malformed control 400 `malformed.json`; schema provably intact between rejections; explicit `purge:false` disable 204 side-effect-free; explicit `purge:true` 204 |
| 2 | Escaped/`%` oracle re-run, both sides (R31/R32 matrices) | `oracle-rerun/` | **39/41 probes EQUAL** (incl. the `w1` broken-`$2`-transform positive proof over the wire on both sides); 2 divergences, both registered: `t6` = D-33, `r1` = **D-34** (new this round: naive-client unencoded `%` — legacy Tomcat 9 lenient 200-empty vs port Tomcat 10.1 container-level 400; dossier entry + harness probe `r32-raw-unencoded-pct` added) |
| 3 | Full-probe harness both sides + adoption leg, `diff-runs.sh` exit 0 | `harness/` | legacy tenant `rvl`: populate OK + **44/44** pins; port fresh-DDL tenant `rvp`: populate OK + **44/44**; adoption enable of `rvl` on the port: 204 + **44/44**; `diff-runs.sh legacy vs port-adopt-rvl`: **exit 0 — byte-equal 21, sorted-equal 9 (D-4 order only), allowed 14 (all registered, incl. D-30/D-32/D-33), diverged 0** |
| 4 | Proxy credential suite (R33 self-test) | `proxy-selftest.txt` | **33/33 PASS** — every credential channel (headers incl. `*-authorization`, query params, capture-failure markers) redacted; forwarding verbatim |
| 5 | Data-continuity pins | `continuity.txt` | `AdoptedSchemaUpgradeIT` **8/8** (37-table before/after continuity vs the committed r13 ground-truth fixture) + `FreshDdlCatalogIT` **4/4**, from the closing-HEAD verify |
| 6 | Rendered k8s service smoke | `k8s-smoke.txt` | `K8sDeploymentTemplateTest` **4/4** (template ports/probes/NetworkPolicy == `server.port`) + kubeconform `-strict` on the envsubst-rendered manifest: **3 resources, Valid 3 / Invalid 0 / Errors 0** (offline equivalent of `kubectl --dry-run=client`, which needs API discovery) |
| 7 | Management-security probe | `management-probe.txt` | over the wire against the rver image: `/admin/loggers` and `/admin/loggers/<name>` GET → 404, the exact F-43 reproduction (unauthenticated POST TRACE flip) → **404**; `/admin/health`, `/admin/metrics`, `/admin/metrics/jvm.memory.used` → 200; `/admin` discovery links list exactly health + metrics; boundary docs in README (R36/R37 sections) + ADR-013 |
| 8 | Semantic live sample (full gate, fresh verdicts on every M9-touched spec) | `../semantic/rver/` | structural 166 files `pass: true` (1 advisory warning: ADR-013 has no architecture-component context); gate **PASS, 249/249 findings PASS, 0 escalation-eligible** — **19 fresh `model` verdicts** on exactly the M9-touched pairs (cache evicted pre-run), 230 cache |
| 9 | One real-Okapi failure/rollback/resume wave with the corrected artifacts | `okapi-wave/` | rollout lane (**unmodified `rollout.sh`**, real dev-mode Okapi): **ALL 10 lane gates passed, exit 0** — control wave `complete` PASS; forced catalog-gate FAIL + ABORT with automatic verified Okapi rollback; documented repair + resume `complete` PASS in a fresh run dir; 15 failed-run evidence files byte-identical after resume; final routing both tenants → 5.0.0 |

Cross-cutting: `forensics.md` (every documented evidence hash recomputed —
84/84 r18 bodies, r20 pins, all four semantic corpus pins, descriptor pins,
132/132 own-bundle bodies, credential sweep zero hits).

## R-VER-traced harness changes (the only non-`docs/evidence` diffs this round)

1. `harness/populate.sh` — the "consume from the first listed (seeded)
   generator" step selected `.[0]` of the code-sorted listing; the M9 Round-1
   discriminator rows (codes sorting before `inventory_*`, no sequences) made
   `.[0].sequences[0].code` empty and failed the populate. Fixed to select the
   first generator that HAS sequences — the same row (`inventory_accessionNumber`)
   the step consumed pre-M9. Found by this round's gate-3 re-run (first full
   populate since the rows landed); the failed first attempt is preserved in
   the session journal, and the committed `harness/` captures are the clean
   re-run.
2. `harness/probes.tsv` + `deviation-allowlist.tsv` — one row each pinning the
   newly registered **D-34** (`r32-raw-unencoded-pct`), so the deviation is
   continuously verified by future harness runs. The three `harness/` capture
   manifests in this bundle pin the 44-probe list that ran (probe-list sha256
   in each manifest predates the row — documented evolution, R22 precedent).

## Layout

```
rver/
  README.md            # this file
  forensics.md         # recompute of every documented evidence hash
  build.txt            # verify totals + jar/descriptor/image hashes
  purge-matrix/        # gate 1: transcript.txt + per-shape response bodies
  oracle-rerun/        # gate 2: rerun-oracle.py + legacy/ + port/ + compare.txt (own README)
  harness/             # gate 3: tenant-enable bodies, populate + capture evidence (3 runs x 44 probes), diff-runs.txt
  proxy-selftest.txt   # gate 4
  continuity.txt       # gate 5
  k8s-smoke.txt        # gate 6
  management-probe.txt # gate 7
  okapi-wave/          # gate 9: lane run bundle (own README)
```

Gate 8 lives in `../semantic/rver/` (corpus convention). Review №5 convening,
push, and PR remain user-gated.
