# R20 rehearsal — real-Okapi tenant lifecycle, empirical evidence for D-26

Full install → upgrade → disable → re-enable → purge lifecycle for rehearsal
tenant `r20r`, driven through a **real Okapi** (7.0.6, dev mode), with a
logging reverse proxy in front of each module capturing the **exact
`POST /_/tenant` bodies Okapi sends on the wire**. This is the empirical
counterpart to the source-level analysis pinned in
`docs/migration/wire-compat-deviations.md` entry **D-26** (Okapi
`TenantManager`, commit `dd321ba`, `"2.0"` interface case: the `purge`
boolean is always explicit; disable is `{module_from, purge:false}` with no
`module_to`).

Run date: 2026-07-21. Nothing in `tenant-calls/` was reformatted — bodies and
headers are byte-for-byte as received by the proxies (`proxy.py`, included).
See "Evidence hygiene and provenance" below for the one post-run redaction
applied to `proxy-logs/` (R27) and for what this bundle does and does not
prove by itself.

## Rig

| Component | Detail |
|---|---|
| Okapi | `folioorg/okapi:latest` = **7.0.6**, digest `sha256:0a0d3c62…9fd7da`, container `okapi-r20`, `dev` (in-memory) mode, host network, :9130 — see `okapi-identification.txt` |
| Legacy module | `mod-service-interaction-4.4.0-SNAPSHOT` (`_tenant` **1.2**), the long-running host java process on :8080 (R13/R18 rig, jar sha256 `abb37211…9b4c57`) |
| Port module | `mod-service-interaction-5.0.0-SNAPSHOT` (`_tenant` **2.0**), container `msi-port-r20` from the R18 release image `mod-service-interaction:r18` (Id `sha256:decad90cb314…`), :8081 |
| Database | container `testing_pg` (postgres:18, host :54321), db `okapi_modules_test`; tenant schema `r20r_mod_service_interaction` |
| Wire taps | stdlib-python logging reverse proxies (`proxy.py`): :18080 → legacy :8080, :18081 → port :8081; Okapi's discovery entries point at the proxies, so **every** module-bound request crossed them (`proxy-logs/*.jsonl`) |
| Descriptors | pre-built, not rebuilt — ids and sha256 in `descriptors.txt` |

Every Okapi API call and response, in order: `transcript.md`.

## Observed `/_/tenant` bodies vs the D-26 expected shapes

| Lifecycle step | Delivered to | Observed body (verbatim, whitespace-collapsed) | Expected D-26 shape | Verdict |
|---|---|---|---|---|
| (a) install legacy | legacy (1.2) | `{"module_to":"mod-service-interaction-4.4.0-SNAPSHOT","parameters":[{"key":"loadReference","value":"true"},{"key":"loadSample","value":"true"}]}` | 1.x shape: `module_to` + `parameters`, **no `purge` field** (D-26 pins the always-explicit purge to the 2.0 case only) | **MATCH** (module → 201) |
| (b) upgrade to port | port (2.0) | `{"module_to":"mod-service-interaction-5.0.0-SNAPSHOT","module_from":"mod-service-interaction-4.4.0-SNAPSHOT","purge":false,"parameters":[…same two…]}` | upgrade: `{module_to, module_from, purge:false, parameters}` | **MATCH** (module → 204) |
| (e) disable | port (2.0) | `{"module_from":"mod-service-interaction-5.0.0-SNAPSHOT","purge":false}` | disable: `{module_from, purge:false}`, **no `module_to`** | **MATCH** (module → 204) |
| (f) re-enable | port (2.0) | `{"module_to":"mod-service-interaction-5.0.0-SNAPSHOT","purge":false}` | fresh enable: `{module_to, purge:false}`, no `module_from` (nothing enabled after the disable) | **MATCH** (module → 204) |
| (g) purge | port (2.0) | `{"module_from":"mod-service-interaction-5.0.0-SNAPSHOT","purge":true}` | purge: `{module_from, purge:true}` | **MATCH** (module → 204) |

Raw bodies + full request headers: `tenant-calls/NN-<side>-<phase>.body.json`
/ `.headers` (the JSON above is the byte content of those files with only
whitespace collapsed for the table). These five requests are the **only**
`/_/tenant` traffic either proxy saw for the whole run — Okapi 7.0.6 issued
no `GET/DELETE /_/tenant/{id}` job-status calls (each POST completed
synchronously).

## Adopted-tenant Liquibase gate (after (b) upgrade)

`psql/b-post-upgrade-port.txt`: `r20r_mod_service_interaction.databasechangelog`
exists with **14 rows, all `exectype = MARK_RAN`, zero `EXECUTED`** — the
port adopted the legacy-created schema without executing DDL (the
`rollout.sh` adopted-tenant gate). Business row counts identical to the
post-legacy-install snapshot (`psql/a-post-install-legacy.txt`):
app_setting 1, number_generator 8, number_generator_sequence 9,
refdata_category 3, refdata_value 13, widget_type 1, widget_definition 0
(37 tables total). Note: `widget_definition` was 0 already on the legacy
side in this run (re-checked after a settle wait; recorded as observed).

## Disable is side-effect-free on the wire (D-26 core claim)

`psql/e-post-disable.txt`, taken immediately after the port answered **204**
to the `{module_from, purge:false}` body:

- schema `r20r_mod_service_interaction` still present;
- `databasechangelog` count **unchanged at 14 (all MARK_RAN)** — Liquibase
  did **not** run on disable (the `ServintTenantController` interception);
- every business row count unchanged.

Re-enable (f) also left the changelog at 14/MARK_RAN
(`psql/f-post-reenable.txt`) — all changesets already recorded.

## Timer (step (c))

`timers.json` — `GET /_/proxy/tenants/r20r/timers` → 200 with exactly one
entry: id `mod-service-interaction_0`, routing `POST
/servint/numberGenerators/resetYearSequences`, `unit:"hour"`, `delay:"24"`,
`modified:false`. Matches the `_timer` interface in the port descriptor.

## Smoke (step (d))

`GET http://localhost:9130/servint/numberGenerators` with
`X-Okapi-Tenant: r20r` → **200** with the seeded generators (first entry
`serialsManagement_patternNumber`; full body in `transcript.md`). The port
proxy's log confirms the request was served by the port via :18081.

## Purge (step (g))

After the `{module_from, purge:true}` body (204 from the port), the schema
query returns **0 rows** — `r20r_mod_service_interaction` dropped
(`psql/g-post-purge.txt`).

## Bundle layout

```
r20-rehearsal/
  README.md                    # this file
  okapi-identification.txt     # Okapi image tag + digest, /_/version, docker run commands
  descriptors.txt              # both MD ids + sha256 of the descriptor files
  transcript.md                # every Okapi API call + full response, in order
  tenant-calls/                # raw /_/tenant bodies + request headers, verbatim
    01-legacy-install.{body.json,headers}
    02-port-upgrade.{body.json,headers}
    03-port-disable.{body.json,headers}
    04-port-reenable.{body.json,headers}
    05-port-purge.{body.json,headers}
  timers.json                  # raw GET /_/proxy/tenants/r20r/timers response
  psql/                        # per-phase verification output (schema, changelog, row counts)
    a-post-install-legacy.txt  b-post-upgrade-port.txt  e-post-disable.txt
    f-post-reenable.txt        g-post-purge.txt
  proxy-logs/                  # complete JSONL request log of both proxies (1 record redacted post-run — see below)
  proxy.py                     # the reverse-proxy script (R27-hardened; the R20 run used the pre-R27 version — see below)
```

## Evidence hygiene and provenance (R27; review №3 F-34 / F-22)

**Credential redaction (2026-07-22).** Persisted captures must never carry
credential-shaped header values, even dev-rig dummies — the pattern has to be
safe before the tool is reused against a secured deployment. Post-run
treatment of this bundle:

- `proxy-logs/proxy-legacy.jsonl` — exactly **one** record carried an
  `X-Okapi-Token` value (the dev-rig literal `DUMMY` on the pre-flight
  `GET /servint/settings/appSettings`). Its value is redacted in place to
  `[REDACTED sha256:ceec12762e66397b]` (first 16 hex of the sha256 of the
  original value; header name kept). No other record in either JSONL carried
  an `Authorization`/`X-Okapi-Token`/`Cookie` header.
- `tenant-calls/*.headers` — verified to contain **no**
  `Authorization`/`X-Okapi-Token`/`Cookie` headers (Okapi 7.0.6 sent none on
  its `/_/tenant` calls in this rig); the files remain byte-for-byte as
  captured.
- `proxy.py` — hardened in place for future rehearsals (R-CERT uses this
  version): sensitive header values are redacted at capture time (name +
  sha256 prefix, wire forwarding untouched), capture files are written
  atomically (tmp + rename), evidence sinks are preflighted at startup, and
  a persistence failure after a forwarded mutation emits an explicit
  `capture-failed` marker (stderr + `<log>.capture-failed` sidecar) while
  still relaying the true upstream response. The R20 run itself used the
  pre-R27 version, which persisted headers verbatim — hence the one
  redaction above.

Recomputed sha256 of the files this treatment touched (all other bundle
files are unmodified since capture):

| File | sha256 after R27 |
|---|---|
| `proxy-logs/proxy-legacy.jsonl` | `cfe83d7e85f83342e07b5c116fb7e2ed1c5dfb0b67f0fbdaa53c34f23a697d88` |
| `proxy-logs/proxy-port.jsonl` (unchanged, listed for completeness) | `587e10f21bfa0ab998fb6ea7a40d263939e1c4fc126e8feef4a485621a0846bf` |
| `proxy.py` (R27-hardened) | `9e661e194fa7b8211dce96baff4fcd8b05f03ca8f5beb6932b8401374ff991c3` |

**What this bundle proves by itself — and what it does not.** By itself the
bundle carries the observed wire truth: the five verbatim `/_/tenant` bodies
and headers, the complete proxy JSONL logs, the per-phase psql state
snapshots, the timers response, and the full Okapi API transcript — a reader
can verify every D-26 claim in this README against those files without any
rebuild. What the bundle can NOT do is reconstruct the binaries that ran:
the module JARs and images are pinned by recorded sha256/image-Id only, and
a fresh `mvn package`/`docker build` produces different hashes (JARs are
timestamp-nondeterministic), so re-verifying the *artifacts* requires a
rebuild from the pinned git revision plus a re-run — which is exactly what
the R-CERT re-rehearsal does. The descriptors are the exception: their
sha256 in `descriptors.txt` matches the checked-in template output.
