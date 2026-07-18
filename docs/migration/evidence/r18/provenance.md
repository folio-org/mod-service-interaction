# R18 evidence provenance — re-review F-11 closure + F-22 auditable evidence

Final wire-parity rehearsal of the rebuilt release artifact (all M7 fixes
R14–R17 landed) against the real legacy module, using the UNMODIFIED
checked-in harness (`docs/migration/harness/`, frozen). Every probe/status
assertion is made by the harness itself; nothing in this bundle was
hand-edited.

Run date: 2026-07-21 (UTC timestamps in the per-run `manifest.json` files).

## What this bundle proves by itself — and what it does not (R27; review №3 F-22)

By itself the bundle carries the observed wire truth and its integrity
chain: every normalized response body is committed alongside its sha256 in
the per-run `manifest.json`, the `diff-runs.txt` verdict is recomputable
from those bodies, and the harness inputs are pinned by hash — a reader can
re-verify all 28+28+28 probe outcomes and the parity diff from the bundle
alone (review №3's E8 did exactly that; every committed hash held). What
the bundle can NOT do is reconstruct the binaries that produced those
responses: the port jar, the `r18` image, and the legacy jar are pinned by
recorded sha256/image-Id only. A fresh `mvn package` of the same git
revision produces a jar with a DIFFERENT hash — JARs are
timestamp-nondeterministic — so the recorded hashes identify the artifacts
that ran; they do not enable bit-identical reconstruction. Re-verifying the
artifacts therefore requires a rebuild from the pinned revision plus a
re-run of the harness, which is what the R-CERT round performs. The
`semantic-verdicts-summary.{json,md}` here is the gate summary of this run;
the full per-file finding/verdict corpus is retained separately under
`docs/migration/evidence/semantic/`.

## Revision and artifacts

| Item | Value |
|---|---|
| Git revision (working tree) | `2f566cf241aa3483c1a22b0e9bfbe4b94d55cdd3`, branch `feat/migration-01` |
| Port jar | `target/mod-service-interaction-5.0.0-SNAPSHOT.jar`, sha256 `cab5265b7d8f61be560781784253d2c0f5603855197a48d581a59c71f6408286` (built this run: `mvn -B -DskipTests clean package`, BUILD SUCCESS; `target/ModuleDescriptor.json` generated, id `mod-service-interaction-5.0.0-SNAPSHOT`) |
| Port image | `mod-service-interaction:r18`, image Id `sha256:decad90cb314fc80250331be5af23830d5782c8f79af3cc4fe77f04b05e31bb0` (local build from the checked-in `Dockerfile`; no RepoDigest — never pushed). Base image `folioci/alpine-jre-openjdk21@sha256:da9ea37fec673b4ed7341ad60d6470696e28e5176c4b2fd3327c368b91947e4f` |
| Legacy jar | `service/build/libs/mod-service-interaction-4.4.0-SNAPSHOT.jar`, sha256 `abb3721190394d2eea3e5661345b4d0fd8da89add558e0fc9924b26ec79b4c57` (same rig as R13, JVM already running — not restarted) |
| Harness inputs | `probes.tsv` sha256 `fe32cb3bbe2c7ed61939cf76ca0548999108b1e6b407020b39f7628f4562b99c`; `deviation-allowlist.tsv` sha256 `519228f6475ec09e18127772b32270f9eb325591e185b460de4df4ec82915784` (both also recorded per-run in each `manifest.json`) |

## Rig

| Component | Detail |
|---|---|
| Legacy module | `http://localhost:8080`, Grails env `production`, OpenJDK 17 — the R13 rig, left running throughout |
| Port module | container `r18-port` from `mod-service-interaction:r18`, `http://localhost:8081`, attached to docker network `testing_default`, env `DB_HOST=testing_pg DB_PORT=5432 DB_DATABASE=okapi_modules_test DB_USERNAME=folio_admin DB_PASSWORD=folio_admin`; `/admin/health` UP ~10 s after start; stopped and removed after the run |
| Database | container `testing_pg` (`postgres:18`, `tools/testing/docker-compose.yml`), host port 54321, db `okapi_modules_test` — shared by both sides; pre-existing tenants `r13a`/`r13b` untouched |
| Tenants | `r18l` (legacy-populated; later adoption-enabled on the port), `r18p` (port fresh-DDL) — both created fresh this run |

## Tool versions

Docker 29.6.1 (build 8900f1d); Apache Maven 3.9.14 on OpenJDK 21.0.11
(Ubuntu); bash 5.2.21; curl 8.5.0; jq 1.7; no direct psql use (all data
flowed through the modules' REST APIs).

## Command transcript (in order)

```bash
# 0. rig verification (already running — per workstream rules, not recreated)
curl -s -o /dev/null -w '%{http_code}' http://localhost:8080/servint/settings/appSettings \
  -H 'X-Okapi-Tenant: r13b' -H 'X-Okapi-Token: DUMMY' \
  -H 'X-Okapi-User-Id: 11111111-1111-1111-1111-111111111111'        # -> 200
docker ps    # testing_pg (postgres:18, :54321) Up

# 1. build the release artifact + image
mvn -B -DskipTests clean package                                     # BUILD SUCCESS
sha256sum target/mod-service-interaction-5.0.0-SNAPSHOT.jar          # cab5265b…8286
docker build -t mod-service-interaction:r18 .                        # Id sha256:decad90c…1bb0

# 2. boot the port against the same Postgres
docker run -d --name r18-port --network testing_default -p 8081:8081 \
  -e DB_HOST=testing_pg -e DB_PORT=5432 -e DB_DATABASE=okapi_modules_test \
  -e DB_USERNAME=folio_admin -e DB_PASSWORD=folio_admin \
  mod-service-interaction:r18
# poll http://localhost:8081/admin/health until {"status":"UP"}      # UP after ~10 s

# 3. legacy side — fresh tenant r18l (_tenant 1.2)
curl -X POST http://localhost:8080/_/tenant -H 'X-Okapi-Tenant: r18l' \
  -H 'X-Okapi-Token: DUMMY' -H 'Content-Type: application/json' \
  -d '{"module_to":"mod-service-interaction-4.4.0","parameters":[{"key":"loadReference","value":"true"},{"key":"loadSample","value":"true"}]}'   # -> 201
cd docs/migration/harness
OUT_DIR=…/docs/migration/evidence/r18 TENANT=r18l SIDE=legacy ./populate.sh   # POPULATE OK (all steps)
OUT_DIR=…/docs/migration/evidence/r18 TENANT=r18l ./capture.sh legacy         # 28 probes, OK

# 4. port side — fresh tenant r18p (_tenant 2.0, fresh DDL)
curl -X POST http://localhost:8081/_/tenant -H 'X-Okapi-Tenant: r18p' \
  -H 'x-okapi-url: http://localhost:9130' -H 'Content-Type: application/json' \
  -d '{"module_to":"mod-service-interaction-5.0.0","parameters":[{"key":"loadReference","value":"true"},{"key":"loadSample","value":"true"}]}'   # -> 204
OUT_DIR=… TENANT=r18p SIDE=port ./populate.sh                                 # POPULATE OK (all steps)
OUT_DIR=… TENANT=r18p ./capture.sh port                                       # 28 probes, OK

# 5. port adoption of the legacy-populated tenant (harness README step 6;
#    the diff pair must read the SAME schema — see note below)
curl -X POST http://localhost:8081/_/tenant -H 'X-Okapi-Tenant: r18l' \
  -H 'x-okapi-url: http://localhost:9130' -H 'Content-Type: application/json' \
  -d '{"module_from":"mod-service-interaction-4.4.0","module_to":"mod-service-interaction-5.0.0","parameters":[{"key":"loadReference","value":"true"},{"key":"loadSample","value":"true"}]}'   # -> 204
OUT_DIR=… TENANT=r18l ./capture.sh port port-adopt-r18l                       # 28 probes, OK

# 6. diff the legacy and port captures of the same tenant
./diff-runs.sh …/r18/legacy …/r18/port-adopt-r18l > …/r18/diff-runs.txt       # exit 0

# 7. semantic verdict summary
sdd validate --semantic --branch main-final --format json                     # gate PASS, 241/241 PASS
#    -> semantic-verdicts-summary.{json,md}

# 8. teardown (port container only; legacy rig + testing_pg left running)
docker rm -f r18-port
```

## Outcomes

| Phase | Result |
|---|---|
| `populate.sh` legacy (r18l) | all steps passed, fail-closed (dashboard-create 200, widget-def-create 201, D-2 legacy path) |
| `capture.sh legacy` (r18l) | 28/28 probes, every pinned legacy status held — incl. `f21-unbalanced-paren` 500, `f21-gt-notanumber` 500, `f21-10k-filter` 400, all `f14-*` pins |
| `populate.sh` port (r18p) | all steps passed — `dashboard-create` 201 (D-27), `widget-def-create` 405 (D-2) |
| `capture.sh port` (r18p) | 28/28 probes, every pinned port status held — incl. `f21-empty-rhs` 200 with the full 11-row listing (R16 clause-drop fix), `f21-10k-filter` 400 (container-level), `f14-malformed-json` 400, `f14-missing-tenant` 400, `f14-duplicate-code` 409, `my-widgets-A` 404 |
| `capture.sh port` (r18l, adopted) | 28/28 probes, every pinned port status held |
| `diff-runs.sh legacy port-adopt-r18l` | **exit 0** — byte-equal 10, sorted-equal 9 (D-4 order only), allowed 9 (D-1, D-22, D-23, D-24, D-28, D-29, D-30×2, D-31 — every allowlist row exercised), DIVERGED 0 (`diff-runs.txt`) |
| Semantic validation | structural 165 files 0/0; semantic gate PASS — 241 PASS / 0 SUSPECT / 0 FAIL / 0 UNKNOWN (`semantic-verdicts-summary.{json,md}`) |

## Note on the diff pairing

`diff-runs.sh` compares normalized response bodies by sha256, and bodies
carry per-tenant generated UUIDs (seeded generators, refdata, created
entities). Wire parity is therefore only observable when both sides read the
SAME tenant schema — the cutover scenario the harness README prescribes
(populate via legacy, adoption-enable the same tenant on the port, capture
both). The certified diff pair is `legacy/` vs `port-adopt-r18l/`, both on
tenant `r18l`. The independent fresh-DDL port run (`port/`, tenant `r18p`)
certifies the port-side populate/capture pins on a schema created by the
port's own Liquibase DDL; comparing it byte-wise against `legacy/` (a
different schema with different generated ids) is not meaningful and is not
part of the parity claim. The adoption enable of `r18l` on the port
(`module_from` set, changesets MARK_RAN) doubles as evidence for the
R19 adopted-schema path: the port answered all 28 probes on the
legacy-created schema with full parity.

## Bundle layout

```
r18/
  tenant-enable-legacy-r18l.json     # legacy enable response body (201)
  tenant-enable-port-r18p.json       # port fresh enable response body (204, empty)
  tenant-enable-port-r18l-adopt.json # port adoption enable response body (204, empty)
  populate-legacy/                   # populate.sh evidence, tenant r18l via legacy
  populate-port/                     # populate.sh evidence, tenant r18p via port
  legacy/                            # capture.sh legacy, tenant r18l  (manifest.json + jq -S bodies, per-body sha256)
  port/                              # capture.sh port,  tenant r18p  (fresh DDL)
  port-adopt-r18l/                   # capture.sh port,  tenant r18l  (adopted schema — diff pair)
  diff-runs.txt                      # full diff-runs.sh output + exit code
  semantic-verdicts-summary.json     # machine-readable semantic certification
  semantic-verdicts-summary.md       # human-readable twin
  provenance.md                      # this file
```
