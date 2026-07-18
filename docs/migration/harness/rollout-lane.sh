#!/bin/bash
# Recurring rollout rehearsal lane (M9 R38, review-4 F-45) — the named
# pre-release gate that keeps the rollout evidence from being one-off.
#
# Self-contained driver: stands up a DISPOSABLE rig (its own Postgres + a
# dev-mode in-memory Okapi via rollout-lane-compose.yml, plus the two module
# processes on host ports), replays the certified R-CERT scenario end to end
# against the UNMODIFIED rollout.sh, and exits non-zero on any gate miss:
#
#   1. preflight       tools, jars, module descriptors, free ports
#   2. infra           compose up; Postgres healthy; Okapi /_/version 200
#   3. modules         legacy (Grails, _tenant 1.2) + port (Boot, _tenant 2.0)
#                      booted against the lane database and answering
#   4. registration    both MDs registered, discovery entries added, wave
#                      tenants created and LEGACY-enabled through Okapi
#                      install?deploy=true (adoption pre-state), routing
#                      verified
#   5. injection       lanebad deliberately corrupted (the three widget
#                      tables dropped) so the adoption catalog gate MUST fire
#   6. control wave    rollout.sh lane1 lanec        -> exit 0, ledger
#                      'complete' PASS
#   7. forced failure  rollout.sh lane2 lanebad      -> exit 1, ledger
#                      'post-verify-catalog' FAIL, automatic Okapi rollback
#                      re-verified (routing lists MODULE_FROM again)
#   8. repair + resume documented repair (drop the failed attempt's
#                      databasechangelog{,lock}), rollout.sh lane2 lanebad
#                      -> exit 0, 'complete' PASS in a FRESH run directory
#   9. immutability    every evidence file of the failed run re-hashed after
#                      the resume — must be byte-identical
#  10. final routing   both tenants list MODULE_TO
#
# rollout.sh, capture.sh and the probe list are used exactly as committed —
# the lane exercises the certified tooling, it never adapts it.
#
# Prerequisites (checked, not built, by the preflight):
#   - legacy jar   service/build/libs/mod-service-interaction-4.4.0-SNAPSHOT.jar
#                  (cd service && ./gradlew assemble)  + its generated MD at
#                  service/build/resources/main/okapi/ModuleDescriptor.json
#   - port jar     target/mod-service-interaction-5.0.0-SNAPSHOT.jar
#                  (mvn -B package)                    + target/ModuleDescriptor.json
#   - docker + docker compose, curl, jq, python3, sha256sum
#   - a Java 17 for the legacy jar (LANE_JAVA17) and Java 21+ for the port
#
# Env (all optional):
#   LANE_PG_PORT      lane Postgres host port            (default 54329)
#   LANE_OKAPI_PORT   lane Okapi port (host network)     (default 9138)
#   LANE_LEGACY_PORT  legacy module port                 (default 8086)
#   LANE_PORT_PORT    port module port                   (default 8087)
#   LANE_OKAPI_IMAGE  Okapi image                        (default folioorg/okapi:latest)
#   LANE_JAVA17       java binary for the legacy jar    (default
#                     /usr/lib/jvm/java-17-openjdk-amd64/bin/java, else java)
#   LANE_JAVA21       java binary for the port jar      (default java)
#   LANE_OUT          output root for logs + evidence    (default: fresh
#                     mktemp -d /tmp/msi-rollout-lane.XXXXXX)
#   LANE_KEEP         true -> leave the rig running on exit (debugging)
#
# The Okapi operator token is a generated JWT-shaped DUMMY (Okapi dev mode
# parses supplied tokens and rejects bare words); it is exported as
# OKAPI_TOKEN for rollout.sh, which never persists it (F-34/F-35 hygiene).
#
# Exit: 0 every gate passed; 1 any gate missed; 2 preflight/config error.

set -u
cd "$(dirname "$0")"
HARNESS_DIR=$PWD
ROOT=$(cd ../../.. && pwd)

LANE_PG_PORT=${LANE_PG_PORT:-54329}
LANE_OKAPI_PORT=${LANE_OKAPI_PORT:-9138}
LANE_LEGACY_PORT=${LANE_LEGACY_PORT:-8086}
LANE_PORT_PORT=${LANE_PORT_PORT:-8087}
LANE_OKAPI_IMAGE=${LANE_OKAPI_IMAGE:-folioorg/okapi:latest}
LANE_KEEP=${LANE_KEEP:-false}
export LANE_PG_PORT LANE_OKAPI_PORT LANE_OKAPI_IMAGE

LEGACY_JAR=$ROOT/service/build/libs/mod-service-interaction-4.4.0-SNAPSHOT.jar
LEGACY_MD=$ROOT/service/build/resources/main/okapi/ModuleDescriptor.json
PORT_JAR=$ROOT/target/mod-service-interaction-5.0.0-SNAPSHOT.jar
PORT_MD=$ROOT/target/ModuleDescriptor.json
LEGACY_ID=mod-service-interaction-4.4.0-SNAPSHOT
PORT_ID=mod-service-interaction-5.0.0-SNAPSHOT

TENANT_OK=lanec
TENANT_BAD=lanebad
SCHEMA_BAD=${TENANT_BAD}_mod_service_interaction

OKAPI=http://localhost:$LANE_OKAPI_PORT
COMPOSE=(docker compose -f "$HARNESS_DIR/rollout-lane-compose.yml")

LEGACY_PID='' PORT_PID=''

say()  { echo "[lane] $*"; }
gate() { echo "[lane] [gate] $*"; }

teardown() {
  if [ "$LANE_KEEP" = true ]; then
    say "LANE_KEEP=true — rig left running (legacy pid $LEGACY_PID, port pid $PORT_PID)"
    return
  fi
  say "teardown: stopping modules and disposing of the lane rig"
  [ -n "$PORT_PID" ] && kill "$PORT_PID" 2>/dev/null
  [ -n "$LEGACY_PID" ] && kill "$LEGACY_PID" 2>/dev/null
  docker logs msi-lane-okapi > "$LANE_OUT/okapi.log" 2>&1 || true
  "${COMPOSE[@]}" down -v >/dev/null 2>&1 || true
}

fail() {
  echo "[lane] FAIL: $*" >&2
  echo "[lane] evidence and logs under: $LANE_OUT" >&2
  teardown
  exit 1
}

wait_http() { # wait_http NAME URL TIMEOUT_S [expected_code]
  local name=$1 url=$2 timeout=$3 expect=${4:-} code deadline
  deadline=$(( $(date +%s) + timeout ))
  while [ "$(date +%s)" -lt "$deadline" ]; do
    code=$(curl -s -o /dev/null -w '%{http_code}' --connect-timeout 2 --max-time 5 "$url" || true)
    if [ -n "$expect" ]; then
      [ "$code" = "$expect" ] && return 0
    else
      [ "$code" != 000 ] && [ -n "$code" ] && return 0
    fi
    sleep 2
  done
  return 1
}

okapi_post() { # okapi_post URL BODYFILE_OR_INLINE EXPECT [extra curl args...]
  local url=$1 body=$2 expect=$3 code
  shift 3
  code=$(curl -s -o "$LANE_OUT/last-okapi-response.json" -w '%{http_code}' \
    --connect-timeout 5 --max-time 300 -X POST "$url" \
    -H 'Content-Type: application/json' -H "X-Okapi-Token: $OKAPI_TOKEN" \
    -d "$body" "$@")
  [ "$code" = "$expect" ] || {
    say "POST $url -> $code (expected $expect): $(command cat "$LANE_OUT/last-okapi-response.json")"
    return 1
  }
}

tenant_modules() { # tenant_modules TENANT -> module id list on stdout
  curl -s --connect-timeout 5 --max-time 30 -H "X-Okapi-Token: $OKAPI_TOKEN" \
    "$OKAPI/_/proxy/tenants/$1/modules" | jq -r '.[].id'
}

lane_psql() { # lane_psql SQL — runs inside the lane-pg container
  docker exec -i msi-lane-pg psql -X -q -At -v ON_ERROR_STOP=1 \
    -U folio_admin -d okapi_modules_lane -c "$1"
}

# --- gate 1: preflight -------------------------------------------------------
MISSING=()
for tool in docker curl jq python3 sha256sum; do
  command -v "$tool" >/dev/null || MISSING+=("$tool")
done
docker compose version >/dev/null 2>&1 || MISSING+=("docker-compose-plugin")
[ ${#MISSING[@]} -eq 0 ] || { echo "[lane] missing tools: ${MISSING[*]}" >&2; exit 2; }
for f in "$LEGACY_JAR" "$LEGACY_MD" "$PORT_JAR" "$PORT_MD"; do
  [ -f "$f" ] || { echo "[lane] missing artifact: $f (see prerequisites in the header)" >&2; exit 2; }
done

LANE_JAVA17=${LANE_JAVA17:-/usr/lib/jvm/java-17-openjdk-amd64/bin/java}
[ -x "$LANE_JAVA17" ] || LANE_JAVA17=java
LANE_JAVA21=${LANE_JAVA21:-java}
"$LANE_JAVA17" -version 2>&1 | command grep -q 'version "17' \
  || { echo "[lane] LANE_JAVA17 ($LANE_JAVA17) is not a Java 17" >&2; exit 2; }

for p in "$LANE_PG_PORT" "$LANE_OKAPI_PORT" "$LANE_LEGACY_PORT" "$LANE_PORT_PORT"; do
  if curl -s -o /dev/null --connect-timeout 1 --max-time 2 "http://localhost:$p/"; then
    echo "[lane] port $p is already in use — set LANE_*_PORT overrides" >&2; exit 2
  fi
done

LANE_OUT=${LANE_OUT:-$(mktemp -d /tmp/msi-rollout-lane.XXXXXX)}
mkdir -p "$LANE_OUT/bin"
gate "1 preflight: tools, artifacts, java, free ports — OK; output: $LANE_OUT"

# JWT-shaped dummy operator token (see header). base64url, no padding.
b64url() { printf '%s' "$1" | base64 -w0 | tr '+/' '-_' | tr -d '='; }
OKAPI_TOKEN="$(b64url '{"alg":"none"}').$(b64url '{"sub":"rollout-lane"}').lane"
export OKAPI_TOKEN

trap teardown INT TERM

# --- gate 2: infra -----------------------------------------------------------
"${COMPOSE[@]}" up -d --wait lane-pg || fail "compose could not start lane-pg healthy"
"${COMPOSE[@]}" up -d lane-okapi || fail "compose could not start lane-okapi"
wait_http okapi "$OKAPI/_/version" 60 200 || fail "Okapi did not answer /_/version on :$LANE_OKAPI_PORT"
gate "2 infra: lane-pg healthy, Okapi $(curl -s "$OKAPI/_/version") on :$LANE_OKAPI_PORT — OK"

# psql shim for rollout.sh (the host may have no psql client; same pattern as
# the R-CERT rig): forwards every call to the client inside lane-pg.
command cat > "$LANE_OUT/bin/psql" <<'SHIM'
#!/bin/bash
exec docker exec -i msi-lane-pg psql -U folio_admin -d okapi_modules_lane "$@"
SHIM
chmod +x "$LANE_OUT/bin/psql"

# --- gate 3: modules ---------------------------------------------------------
say "booting legacy module ($LEGACY_ID) on :$LANE_LEGACY_PORT ..."
"$LANE_JAVA17" \
  -Ddb.host=localhost -Ddb.port="$LANE_PG_PORT" -Ddb.database=okapi_modules_lane \
  -Ddb.username=folio_admin -Ddb.password=folio_admin \
  -Dserver.port="$LANE_LEGACY_PORT" \
  -jar "$LEGACY_JAR" > "$LANE_OUT/legacy.log" 2>&1 &
LEGACY_PID=$!

say "booting port module ($PORT_ID) on :$LANE_PORT_PORT ..."
DB_HOST=localhost DB_PORT=$LANE_PG_PORT DB_DATABASE=okapi_modules_lane \
DB_USERNAME=folio_admin DB_PASSWORD=folio_admin SERVER_PORT=$LANE_PORT_PORT \
  "$LANE_JAVA21" -jar "$PORT_JAR" > "$LANE_OUT/port.log" 2>&1 &
PORT_PID=$!

wait_http port "http://localhost:$LANE_PORT_PORT/admin/health" 180 200 \
  || fail "port module never answered /admin/health (see $LANE_OUT/port.log)"
wait_http legacy "http://localhost:$LANE_LEGACY_PORT/" 300 \
  || fail "legacy module never answered HTTP (see $LANE_OUT/legacy.log)"
gate "3 modules: legacy pid $LEGACY_PID + port pid $PORT_PID answering — OK"

# --- gate 4: Okapi registration + adoption pre-state -------------------------
okapi_post "$OKAPI/_/proxy/modules" "@$LEGACY_MD" 201 || fail "legacy MD registration"
okapi_post "$OKAPI/_/proxy/modules" "@$PORT_MD" 201 || fail "port MD registration"
okapi_post "$OKAPI/_/discovery/modules" \
  "{\"instId\":\"lane-legacy\",\"srvcId\":\"$LEGACY_ID\",\"url\":\"http://localhost:$LANE_LEGACY_PORT\"}" 201 \
  || fail "legacy discovery entry"
okapi_post "$OKAPI/_/discovery/modules" \
  "{\"instId\":\"lane-port\",\"srvcId\":\"$PORT_ID\",\"url\":\"http://localhost:$LANE_PORT_PORT\"}" 201 \
  || fail "port discovery entry"

TP_QUERY='tenantParameters=loadReference%3Dtrue%2CloadSample%3Dtrue'
for t in "$TENANT_OK" "$TENANT_BAD"; do
  okapi_post "$OKAPI/_/proxy/tenants" "{\"id\":\"$t\"}" 201 || fail "tenant $t creation"
  okapi_post "$OKAPI/_/proxy/tenants/$t/install?deploy=true&$TP_QUERY" \
    "[{\"id\":\"$LEGACY_ID\",\"action\":\"enable\"}]" 200 \
    || fail "legacy enable of $t (see $LANE_OUT/legacy.log)"
  tenant_modules "$t" | command grep -qx "$LEGACY_ID" || fail "routing after legacy enable of $t"
done
gate "4 registration: MDs + discovery + tenants $TENANT_OK,$TENANT_BAD legacy-enabled, routing verified — OK"

# --- gate 5: forced-failure injection ----------------------------------------
lane_psql "DROP TABLE $SCHEMA_BAD.widget_instance" >/dev/null || fail "injection: widget_instance"
lane_psql "DROP TABLE $SCHEMA_BAD.widget_definition" >/dev/null || fail "injection: widget_definition"
lane_psql "DROP TABLE $SCHEMA_BAD.widget_type" >/dev/null || fail "injection: widget_type"
TABLES=$(lane_psql "SELECT count(*) FROM information_schema.tables WHERE table_schema='$SCHEMA_BAD' AND table_type='BASE TABLE'")
[ "$TABLES" = 34 ] || fail "injection left $TABLES tables in $SCHEMA_BAD (expected 34)"
gate "5 injection: $SCHEMA_BAD corrupted to 34 tables — OK"

# --- rollout.sh environment (the certified script, unmodified) ---------------
export PORT_URL=http://localhost:$LANE_PORT_PORT
export MODULE_TO=$PORT_ID MODULE_FROM=$LEGACY_ID
export OKAPI_URL=$OKAPI
export PGHOST=localhost PGPORT=$LANE_PG_PORT PGDATABASE=okapi_modules_lane
export PGUSER=folio_admin PGPASSWORD=folio_admin
export TENANT_PARAMETERS='[{"key":"loadReference","value":"true"},{"key":"loadSample","value":"true"}]'
export LEGACY_URL=http://localhost:$LANE_LEGACY_PORT
export PRE_PROBES=$HARNESS_DIR/rollout-lane-preprobes.tsv
export OUT_DIR=$LANE_OUT/rollout-evidence
LEDGER=$OUT_DIR/rollout-ledger.csv
PATH=$LANE_OUT/bin:$PATH

# --- gate 6: control wave ----------------------------------------------------
./rollout.sh lane1 "$TENANT_OK" > "$LANE_OUT/rollout-lane1.log" 2>&1
rc=$?
[ $rc -eq 0 ] || fail "control wave exited $rc (see $LANE_OUT/rollout-lane1.log)"
command grep -q ",\"$TENANT_OK\",\"complete\",\"PASS\"," "$LEDGER" \
  || fail "control wave: no 'complete' PASS ledger row for $TENANT_OK"
gate "6 control wave: exit 0, $TENANT_OK complete PASS — OK"

# --- gate 7: forced failure + automatic rollback -----------------------------
./rollout.sh lane2 "$TENANT_BAD" > "$LANE_OUT/rollout-lane2-fail.log" 2>&1
rc=$?
[ $rc -eq 1 ] || fail "forced-failure wave exited $rc (expected 1)"
command grep -q ",\"$TENANT_BAD\",\"post-verify-catalog\",\"FAIL\"," "$LEDGER" \
  || fail "forced failure: no post-verify-catalog FAIL ledger row for $TENANT_BAD"
tenant_modules "$TENANT_BAD" | command grep -qx "$LEGACY_ID" \
  || fail "rollback: $TENANT_BAD does not route to $LEGACY_ID again"
tenant_modules "$TENANT_BAD" | command grep -qx "$PORT_ID" \
  && fail "rollback: $TENANT_BAD still routes to $PORT_ID"
FAILED_RUN_DIR=$(find "$OUT_DIR/lane2" -mindepth 1 -maxdepth 1 -type d | command head -1)
[ -n "$FAILED_RUN_DIR" ] || fail "forced failure: no run directory under $OUT_DIR/lane2"
(cd "$FAILED_RUN_DIR" && find . -type f -print0 | sort -z | xargs -0 sha256sum) \
  > "$LANE_OUT/immutability-before.txt"
gate "7 forced failure: exit 1, catalog gate FAIL, Okapi rollback re-verified — OK"

# --- gate 8: repair + resume -------------------------------------------------
lane_psql "DROP TABLE $SCHEMA_BAD.databasechangelog" >/dev/null || fail "repair: databasechangelog"
lane_psql "DROP TABLE $SCHEMA_BAD.databasechangeloglock" >/dev/null || fail "repair: databasechangeloglock"
./rollout.sh lane2 "$TENANT_BAD" > "$LANE_OUT/rollout-lane2-resume.log" 2>&1
rc=$?
[ $rc -eq 0 ] || fail "resume wave exited $rc (see $LANE_OUT/rollout-lane2-resume.log)"
RESUME_ROWS=$(command grep -c ",\"$TENANT_BAD\",\"complete\",\"PASS\"," "$LEDGER")
[ "$RESUME_ROWS" -ge 1 ] || fail "resume: no 'complete' PASS ledger row for $TENANT_BAD"
gate "8 repair + resume: exit 0, $TENANT_BAD complete PASS in a fresh run dir — OK"

# --- gate 9: failed-run evidence immutability --------------------------------
(cd "$FAILED_RUN_DIR" && find . -type f -print0 | sort -z | xargs -0 sha256sum) \
  > "$LANE_OUT/immutability-after.txt"
diff -u "$LANE_OUT/immutability-before.txt" "$LANE_OUT/immutability-after.txt" \
  || fail "immutability: the failed run's evidence changed during the resume"
gate "9 immutability: $(command grep -c . "$LANE_OUT/immutability-before.txt") files of the failed run byte-identical — OK"

# --- gate 10: final routing --------------------------------------------------
for t in "$TENANT_OK" "$TENANT_BAD"; do
  tenant_modules "$t" | command grep -qx "$PORT_ID" || fail "final routing: $t does not list $PORT_ID"
done
gate "10 final routing: $TENANT_OK + $TENANT_BAD list $PORT_ID — OK"

say "ALL 10 GATES PASSED — evidence under $LANE_OUT"
teardown
exit 0
