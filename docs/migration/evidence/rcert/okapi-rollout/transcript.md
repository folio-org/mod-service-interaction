# R-CERT Okapi rollout rehearsal transcript — 2026-07-22T19:05:12Z

All Okapi calls carry an X-Okapi-Token header (authenticated-run rehearsal;
Okapi 7.0.6 dev parses any supplied token and requires JWT shape — a bare
word like DUMMY is rejected 400 'Missing . separator', so the operator token
is a JWT-shaped dummy). The token VALUE appears nowhere in this bundle:
commands reference $OKAPI_TOKEN per the F-34/F-35 hygiene rule.

## 1. Register module descriptors
```
POST /_/proxy/modules (legacy MD, sha256 2f94a80e...)  -> HTTP 201 (first attempt; recorded)
POST /_/proxy/modules (port MD target/ModuleDescriptor.json, rebuilt this run, sha256 6c14c195...)
HTTP 201
```

## 2. Discovery entries (URLs point at the logging proxies — every module-bound call crosses the wire taps)
```
{
  "instId" : "rcert-legacy",
  "srvcId" : "mod-service-interaction-4.4.0-SNAPSHOT",
  "url" : "http://localhost:18080"
}
HTTP 201
{
  "instId" : "rcert-port",
  "srvcId" : "mod-service-interaction-5.0.0-SNAPSHOT",
  "url" : "http://localhost:18081"
}
HTTP 201
```

## 3. Wave tenants: create in Okapi + enable LEGACY module (adoption pre-state)
```
tenant rcw1a: create -> 201; install legacy (deploy=true, tenantParameters loadReference,loadSample) -> 200; /modules now: mod-service-interaction-4.4.0-SNAPSHOT,okapi-7.0.6
tenant rcw1b: create -> 201; install legacy (deploy=true, tenantParameters loadReference,loadSample) -> 200; /modules now: mod-service-interaction-4.4.0-SNAPSHOT,okapi-7.0.6
tenant rcw2ok: create -> 201; install legacy (deploy=true, tenantParameters loadReference,loadSample) -> 200; /modules now: mod-service-interaction-4.4.0-SNAPSHOT,okapi-7.0.6
tenant rcw2bad: create -> 201; install legacy (deploy=true, tenantParameters loadReference,loadSample) -> 200; /modules now: mod-service-interaction-4.4.0-SNAPSHOT,okapi-7.0.6
```

## 4. Forced-failure injection (before any rollout run)
```
-- rcw2bad deliberately corrupted to force the adoption catalog gate mid-wave:
DROP TABLE rcw2bad_mod_service_interaction.widget_instance;   -- (then widget_definition, widget_type)
-- schema now 34 tables (was 37). The port's adoption enable will EXECUTE the
-- widget changesets (preconditions find no tables), recreating them — a
-- catalog change on an adopted schema, which rollout.sh must catch at
-- post-verify-catalog and answer with an automatic verified Okapi rollback.
```

## 5. Diagnosis + repair between run A and the resume run
```
-- catalog.diff pinned the failure to the three deliberately dropped widget
-- tables, recreated by the port's EXECUTED changesets during the failed
-- attempt (the adoption gate working as designed). The automatic rollback
-- already re-enabled the legacy module (Okapi routing re-verified), and the
-- legacy re-enable reseeded the widget_type reference row. Repair for the
-- resume run removes the failed attempt's port bookkeeping so the retry
-- adopts the (now-complete) schema cleanly:
DROP TABLE rcw2bad_mod_service_interaction.databasechangelog;
DROP TABLE rcw2bad_mod_service_interaction.databasechangeloglock;
-- (production equivalent per runbook 3.3: STOP, investigate, restore from
--  backup; here the corruption was injected deliberately, so the repair is
--  surgical and documented.)
```

## 6. Waves (full rollout.sh stdout in logs/)

| Run | Invocation | Exit | Outcome |
|---|---|---|---|
| control | `rollout.sh rcw1 rcw1a rcw1b` | 0 | both tenants `complete` PASS: pre-capture 2/2, Okapi install 200, routing verified, catalog unchanged, EXECUTED=0 MARK_RAN=14, smoke 2/2 through Okapi |
| forced failure | `rollout.sh rcw2 rcw2ok rcw2bad` | 1 | rcw2ok `complete` PASS mid-wave; rcw2bad enable 200 + routing verified, then `post-verify-catalog` FAIL (catalog.diff = the 3 recreated widget tables, 20 column rows), automatic rollback: Okapi enable module_from -> 200 + routing RE-verified (`[pass] rollback`), HARD_STOP abort regardless of MAX_FAILURES |
| resume | `rollout.sh rcw2 rcw2bad` | 0 | fresh run dir 20260722T190736Z-1336414; rcw2bad `complete` PASS: adoption 14/14 MARK_RAN EXECUTED=0, catalog unchanged, routing verified |

## 7. Post-run verifications
```
run-A immutability: all 28 evidence files of run 20260722T190648Z-1334794
  re-hashed after the resume run — byte-identical (retry never touches prior evidence)
final routing: rcw1a rcw1b rcw2ok rcw2bad all list mod-service-interaction-5.0.0-SNAPSHOT
rollback wire truth: 05-legacy.body.json = {module_to: 4.4.0-SNAPSHOT, module_from: 5.0.0-SNAPSHOT}
  (the inverse transition delivered to the legacy module, 1.2 shape, no purge field)
upgrade wire truth: all five port bodies = {module_to, module_from, purge:false, parameters} (D-26)
timers: GET /_/proxy/tenants/rcw1a/timers -> mod-service-interaction_0,
  POST /servint/numberGenerators/resetYearSequences, unit hour, delay 24
ledger: 9 rows (3 run-start INFO, 4 complete PASS, 1 post-verify-catalog FAIL, 1 stop-condition ABORT);
  X-Okapi-Token appears ONLY as the $OKAPI_TOKEN shell expansion; the token value
  appears in NO file under this bundle (swept)
proxy JSONLs: zero sensitive-header records persisted (Okapi 7.0.6 dev sends no
  auth headers to modules; the operator token stays on the Okapi admin API)
```

## 8. Teardown
```
docker rm -f okapi-rcert          # Okapi was dev-mode (in-memory) — state disposable
kill <proxy pids>                 # wire taps
# rcert-port container + legacy jar + testing_pg left RUNNING (standing rig practice)
```
