#!/bin/bash
# Batch-rollout executor — cutover runbook Phase 4 (review F-24; reworked for
# review №3 F-27/F-28/F-29/F-30/F-35).
#
# Rolls one wave of tenants over to the Spring Boot port, one tenant at a
# time, with a return-code check after EVERY step, an APPEND-ONLY CSV ledger,
# immutable per-run evidence, pre/post catalog+row-count snapshots, wire
# smoke, and automatic per-tenant rollback on failure (runbook Phase 6).
#
# MODES (F-27 — the cutover is the checked step):
#   MODE=okapi (default) — production cutover. Enable runs through Okapi's
#     install API (POST $OKAPI_URL/_/proxy/tenants/<T>/install?deploy=true),
#     then the script VERIFIES routing (GET /_/proxy/tenants/<T>/modules must
#     list MODULE_TO), smokes THROUGH Okapi, and only then appends a
#     'complete' PASS row carrying the observed routing state. Rollback is
#     the inverse Okapi transition (re-enable MODULE_FROM, or disable
#     MODULE_TO when there is no legacy id) followed by a routing re-verify.
#   MODE=direct — explicit opt-in pre-verification pass on a rig WITHOUT
#     Okapi routing. Enable is the module-direct _tenant 2.0 POST; ledger
#     rows say 'verified-direct', NEVER 'complete' — a direct run does not
#     change Okapi routing and is not a cutover.
#
# Per tenant, in order (any failing step -> ledger FAIL row, rollback, stop
# check):
#   a. optional wire pre-capture via capture.sh   -> <run-dir>/<tenant>/pre/
#      (only when LEGACY_URL and PRE_PROBES are set; the skip is recorded)
#   b. catalog + row-count + changelog-count pre-snapshot (runbook 2.1/2.2)
#      via psql; a tenant with no pre-state (schema absent) is recorded as
#      fresh and the comparison in (e) is skipped
#   c. enable per MODE (see above); a transport error is RECONCILED against
#      actual state before classification (okapi: /modules listing; direct:
#      databasechangelog row count) — never silently assumed either way
#   d. MODE=okapi only: routing verification — /_/proxy/tenants/<T>/modules
#      must list MODULE_TO
#   e. post catalog/count snapshot + comparison (runbook 3.3): catalog must
#      be byte-identical for adopted schemas (bookkeeping tables already
#      excluded by the 2.1 query); business row counts must not shrink
#      (growth from live traffic is allowed and recorded); databasechangelog
#      must be MARK_RAN-only on adopted tenants. catalog.diff is always kept
#      (empty file = success evidence — never deleted).
#   f. smoke: fixed read-only tenant-neutral GET probes with pinned statuses
#      (never getNextNumber — runbook 3.4), through Okapi in MODE=okapi,
#      module-direct in MODE=direct. /admin/health is checked once per run,
#      before the first tenant.
#
# EVIDENCE (F-28 — immutable): every run gets a fresh run directory
#   $OUT_DIR/<wave>/<run-id>/<tenant>/
# with run-id = <utc-stamp>-<pid>; an existing run directory is REFUSED
# (never reused, never overwritten). A retry is a new run-id — earlier
# evidence stays intact. Every evidence file's sha256 is recorded in the
# tenant's ledger row (and in <tenant>/sha256sums.txt).
#
# LEDGER (append-only): header
#   utc,wave,run_id,tenant,phase,result,detail,evidence_sha256
# written once on creation; rows only ever appended, NEVER truncated — keep
# one cumulative file across all waves as the audit record. (Ledgers written
# before this revision used a 6-column schema; new rows simply carry the two
# extra columns.) Every run begins with a 'run-start' INFO row — that append
# doubles as the ledger writability probe (F-29).
#
# Usage:   rollout.sh <wave-id> [tenant ...]
#          Tenant ids from the arguments, else one per line from
#          $TENANTS_FILE ('#' comments and blank lines ignored).
#          Tenant normalization (F-30): OUTER whitespace is trimmed; a
#          tenant with INTERNAL whitespace is rejected (never squashed);
#          duplicates are rejected; length must satisfy
#          len(tenant) + 24 <= 63 (the '_mod_service_interaction' schema
#          suffix vs the Postgres 63-byte identifier limit).
#
# Env (required — validated up-front, all missing/invalid vars listed):
#   PORT_URL      base URL of a running 5.0.0 port instance (health check)
#   MODULE_TO     module id being enabled (e.g. mod-service-interaction-5.0.0);
#                 must match ^[A-Za-z0-9_.-]+$ (F-29)
#   PGHOST PGDATABASE PGUSER
#                 Postgres connection, standard psql variables (PGPORT
#                 defaults to 5432; PGPASSWORD via env or ~/.pgpass)
#   TENANTS_FILE  wave tenant list (only when no tenant arguments are given)
#   OKAPI_URL     Okapi base URL. Required for EVERY real (non-dry) run in
#                 both modes (F-35 — rollback exposure needs an executable
#                 recovery path); required even for DRY_RUN in MODE=okapi.
#   python3       must be on PATH (JSON construction + validation, F-29)
# Env (optional):
#   MODE          okapi (default) | direct — see MODES above
#   MODULE_FROM   exact legacy module id (runbook Phase 0) — the rollback
#                 target in MODE=okapi and the module_from of the direct
#                 enable; must match ^[A-Za-z0-9_.-]+$ when set
#   OKAPI_TOKEN   X-Okapi-Token value (default DUMMY, as the other harness
#                 scripts). NEVER persisted into the ledger: recorded manual
#                 commands reference $OKAPI_TOKEN so they run verbatim in a
#                 shell where it is exported (credential hygiene — the
#                 ledger is evidence, secrets don't belong in it)
#   TENANT_PARAMETERS
#                 JSON array of {"key":...,"value":...} objects (the
#                 _tenant body "parameters" shape; converted to Okapi's
#                 tenantParameters query string in MODE=okapi). Validated
#                 as JSON up-front; malformed input is rejected even under
#                 DRY_RUN (F-29)
#   LEGACY_URL + PRE_PROBES
#                 enable step (a): capture.sh runs against legacy with probe
#                 list $PRE_PROBES into <run-dir>/<tenant>/pre/. Use a
#                 production-safe, read-only probe subset — the full
#                 probes.tsv contains mutating and populate-dependent rows.
#   OUT_DIR       evidence root (default $PWD/evidence)
#   LEDGER        ledger path (default $OUT_DIR/rollout-ledger.csv)
#   MAX_FAILURES  abort the run once this many tenants have failed (default
#                 1). Catalog/count/changelog verification failures AND a
#                 failed rollback abort immediately regardless (F-29).
#   CURL_CONNECT_TIMEOUT / CURL_MAX_TIME / CURL_MAX_TIME_INSTALL
#                 curl timeouts in seconds (defaults 5 / 60 / 600); applied
#                 to every HTTP call (F-29 — no indefinite hangs)
#   DRY_RUN       true -> print every curl/psql/capture command and every
#                 ledger row without executing any side effect; exits 0 on a
#                 well-formed configuration, non-zero on a malformed one.
# Exit:    0 wave completed and every tenant passed (or DRY_RUN, config OK);
#          1 stop condition reached / any tenant failed / preflight failed;
#          2 usage or configuration error.
set -euo pipefail

usage() { echo "usage: rollout.sh <wave-id> [tenant ...]" >&2; exit 2; }

WAVE=${1:-}
[ -n "$WAVE" ] || usage
shift

say()  { echo "rollout: $*"; }
warn() { echo "rollout: $*" >&2; }
now_utc() { date -u +%Y-%m-%dT%H:%M:%SZ; }
q() { printf '%q ' "$@"; }               # copy-pasteable rendering of a command
is_dry() { [ "$DRY_RUN" = true ]; }

# --- configuration (fail fast, list every missing/invalid variable) ----------
DRY_RUN=${DRY_RUN:-false}
case "$DRY_RUN" in true|false) ;; *)
  warn "DRY_RUN must be 'true' or 'false' (got '$DRY_RUN')"; exit 2 ;;
esac
MODE=${MODE:-okapi}
case "$MODE" in okapi|direct) ;; *)
  warn "MODE must be 'okapi' (production cutover) or 'direct' (pre-verification; got '$MODE')"; exit 2 ;;
esac
if ! [[ "$WAVE" =~ ^[A-Za-z0-9._-]+$ ]]; then
  warn "wave id '$WAVE' must match ^[A-Za-z0-9._-]+\$ (it names the evidence directory)"; exit 2
fi

MISSING=()
for v in PORT_URL MODULE_TO PGHOST PGDATABASE PGUSER; do
  if [ -z "${!v:-}" ]; then MISSING+=("$v"); fi
done
if [ $# -eq 0 ] && [ -z "${TENANTS_FILE:-}" ]; then
  MISSING+=("TENANTS_FILE (or tenant ids as arguments)")
fi
# F-35: rollback exposure (any real enable) demands an executable Okapi-side
# recovery path; MODE=okapi cannot even render its commands without it.
if [ -z "${OKAPI_URL:-}" ]; then
  if [ "$MODE" = okapi ] || ! is_dry; then
    MISSING+=("OKAPI_URL (required for MODE=okapi and for every non-dry run)")
  fi
fi
if ! command -v python3 >/dev/null 2>&1; then
  MISSING+=("python3 on PATH (JSON construction/validation)")
fi
if [ ${#MISSING[@]} -gt 0 ]; then
  warn "missing required configuration: ${MISSING[*]}"
  warn "the environment contract is documented in the header of this script"
  exit 2
fi

MODULE_FROM=${MODULE_FROM:-}
OKAPI_URL=${OKAPI_URL:-}
OKAPI_TOKEN=${OKAPI_TOKEN:-DUMMY}
TENANT_PARAMETERS=${TENANT_PARAMETERS:-}
LEGACY_URL=${LEGACY_URL:-}
PRE_PROBES=${PRE_PROBES:-}
OUT_DIR=${OUT_DIR:-$PWD/evidence}
LEDGER=${LEDGER:-$OUT_DIR/rollout-ledger.csv}
MAX_FAILURES=${MAX_FAILURES:-1}
case "$MAX_FAILURES" in ''|0|*[!0-9]*)
  warn "MAX_FAILURES must be a positive integer (got '$MAX_FAILURES')"; exit 2 ;;
esac
CURL_CONNECT_TIMEOUT=${CURL_CONNECT_TIMEOUT:-5}
CURL_MAX_TIME=${CURL_MAX_TIME:-60}
CURL_MAX_TIME_INSTALL=${CURL_MAX_TIME_INSTALL:-600}
for v in CURL_CONNECT_TIMEOUT CURL_MAX_TIME CURL_MAX_TIME_INSTALL; do
  case "${!v}" in ''|*[!0-9]*)
    warn "$v must be a positive integer number of seconds (got '${!v}')"; exit 2 ;;
  esac
done
export PGHOST PGDATABASE PGUSER
export PGPORT=${PGPORT:-5432}
SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)

# F-29: module ids feed JSON bodies and URLs; TENANT_PARAMETERS must be a
# JSON array of {"key":...} objects. Reject malformed input up-front — DRY_RUN
# included (a dry run that renders a malformed body rehearses nothing).
if ! CONFIG_ERR=$(python3 - "$MODULE_TO" "$MODULE_FROM" "$TENANT_PARAMETERS" <<'PY'
import json, re, sys
mt, mf, tp = sys.argv[1], sys.argv[2], sys.argv[3]
pat = re.compile(r'^[A-Za-z0-9_.-]+$')
errs = []
if not pat.match(mt):
    errs.append("MODULE_TO %r is not a valid module id (want ^[A-Za-z0-9_.-]+$)" % mt)
if mf and not pat.match(mf):
    errs.append("MODULE_FROM %r is not a valid module id (want ^[A-Za-z0-9_.-]+$)" % mf)
if tp:
    try:
        v = json.loads(tp)
        if not isinstance(v, list) or not all(isinstance(e, dict) and 'key' in e for e in v):
            errs.append("TENANT_PARAMETERS must be a JSON array of objects each carrying 'key'")
    except ValueError as e:
        errs.append("TENANT_PARAMETERS is not valid JSON: %s" % e)
if errs:
    print("; ".join(errs))
    sys.exit(1)
PY
); then
  warn "invalid configuration: $CONFIG_ERR"
  exit 2
fi

# --- tenant list normalization (F-30) ----------------------------------------
# Trim OUTER whitespace only; internal whitespace is a data error, not
# something to silently squash ('row 3ctl' must never become 'row3ctl').
trim() {
  local s=$1
  s="${s#"${s%%[![:space:]]*}"}"
  s="${s%"${s##*[![:space:]]}"}"
  printf '%s' "$s"
}

RAW_TENANTS=()
if [ $# -gt 0 ]; then
  RAW_TENANTS=("$@")
else
  [ -r "$TENANTS_FILE" ] || { warn "TENANTS_FILE not readable: $TENANTS_FILE"; exit 2; }
  while IFS= read -r line || [ -n "$line" ]; do
    line=${line%%#*}
    line=$(trim "$line")
    if [ -n "$line" ]; then RAW_TENANTS+=("$line"); fi
  done < "$TENANTS_FILE"
fi
TENANTS=()
declare -A SEEN_TENANT=()
for raw in "${RAW_TENANTS[@]}"; do
  t=$(trim "$raw")
  [ -n "$t" ] || continue
  if [[ "$t" =~ [[:space:]] ]]; then
    warn "invalid tenant id '$raw': internal whitespace (fix the wave list — ids are never squashed)"; exit 2
  fi
  # tenant ids feed schema names into SQL literals — enforce the FOLIO shape;
  # len <= 39 keeps len(tenant) + len('_mod_service_interaction')=24 <= 63
  # (the Postgres identifier limit; a longer id would silently truncate).
  if ! [[ "$t" =~ ^[a-z][a-z0-9_]{0,38}$ ]]; then
    warn "invalid tenant id '$t' (want ^[a-z][a-z0-9_]{0,38}\$ — max 39 chars so the derived schema fits 63 bytes)"; exit 2
  fi
  if [ -n "${SEEN_TENANT[$t]:-}" ]; then
    warn "duplicate tenant id '$t' in the wave list"; exit 2
  fi
  SEEN_TENANT[$t]=1
  TENANTS+=("$t")
done
if [ ${#TENANTS[@]} -eq 0 ]; then
  warn "no tenants to process (empty wave)"; exit 2
fi

# --- run identity + immutable evidence layout (F-28) -------------------------
RUN_ID="$(date -u +%Y%m%dT%H%M%SZ)-$$"
RUN_DIR="$OUT_DIR/$WAVE/$RUN_ID"
if ! is_dry; then
  mkdir -p "$OUT_DIR/$WAVE"
  if [ -e "$RUN_DIR" ]; then
    warn "run directory already exists: $RUN_DIR — evidence directories are never reused; rerun to get a fresh run-id"
    exit 2
  fi
  mkdir "$RUN_DIR"
fi

# --- ledger (append-only) ----------------------------------------------------
LEDGER_HEADER='utc,wave,run_id,tenant,phase,result,detail,evidence_sha256'
csv_field() { local s=${1//\"/\"\"}; printf '"%s"' "${s//$'\n'/ }"; }

ledger_append() { # ledger_append TENANT PHASE RESULT DETAIL EVIDENCE_SHA256
  local row
  row="$(now_utc),$(csv_field "$WAVE"),$(csv_field "$RUN_ID"),$(csv_field "$1"),$(csv_field "$2"),$(csv_field "$3"),$(csv_field "$4"),$(csv_field "$5")"
  if is_dry; then echo "[dry-run] ledger append: $row"; return 0; fi
  if [ ! -e "$LEDGER" ]; then
    echo "$LEDGER_HEADER" >> "$LEDGER"    # create, never truncate
  elif [ "$(head -n1 "$LEDGER")" != "$LEDGER_HEADER" ]; then
    warn "ledger $LEDGER has a pre-R23 header — appending 8-column rows anyway (append-only)"
  fi
  echo "$row" >> "$LEDGER"
}

evidence_digest() { # evidence_digest DIR -> 'file=sha256;…' (also writes sha256sums.txt)
  local dir=$1
  if is_dry || [ ! -d "$dir" ]; then printf ''; return 0; fi
  (cd "$dir" && find . -type f ! -name sha256sums.txt -print0 | sort -z \
     | xargs -0 -r sha256sum) > "$dir/sha256sums.txt"
  awk '{ f=$2; sub(/^\.\//,"",f); printf "%s%s=%s", (NR>1?";":""), f, $1 }' \
    "$dir/sha256sums.txt"
}

# --- primitives (DRY_RUN prints the exact command instead of running it) -----
PSQL=(psql -X -q -At -v ON_ERROR_STOP=1)

http_code() { # http_code OUTFILE METHOD URL [curl args...] -> prints status or DRY
  local out=$1 method=$2 url=$3
  shift 3
  local args=(-s -S -o "$out" -w '%{http_code}'
              --connect-timeout "$CURL_CONNECT_TIMEOUT" --max-time "$CURL_MAX_TIME"
              -X "$method" "$url" "$@")
  if is_dry; then
    echo "[dry-run] curl $(q "${args[@]}")" >&2
    printf 'DRY'
    return 0
  fi
  curl "${args[@]}" </dev/null
}

psql_to() { # psql_to OUTFILE SQL
  local out=$1 sql=$2
  if is_dry; then echo "[dry-run] $(q "${PSQL[@]}" -c "$sql")> $out"; return 0; fi
  "${PSQL[@]}" -c "$sql" > "$out"
}

psql_val() { # psql_val SQL -> prints the scalar result or DRY
  local sql=$1
  if is_dry; then
    echo "[dry-run] $(q "${PSQL[@]}" -c "$sql")" >&2
    printf 'DRY'
    return 0
  fi
  "${PSQL[@]}" -c "$sql"
}

catalog_sql() { # catalog_sql SCHEMA — runbook 2.1 (bookkeeping excluded, name-sorted)
  printf "SELECT table_name||'|'||column_name||'|'||data_type||'|'||is_nullable||'|'||coalesce(column_default,'') FROM information_schema.columns WHERE table_schema='%s' AND table_name NOT IN ('databasechangelog','databasechangeloglock') ORDER BY table_name, column_name" "$1"
}

counts_snapshot() { # counts_snapshot SCHEMA OUTFILE — runbook 2.2 (table|count lines)
  local s=$1 out=$2 gen
  gen="SELECT format('SELECT %L || ''|'' || count(*) FROM %I.%I;', table_name, table_schema, table_name) FROM information_schema.tables WHERE table_schema='$s' AND table_type='BASE TABLE' AND table_name NOT IN ('databasechangelog','databasechangeloglock') ORDER BY table_name"
  if is_dry; then
    echo "[dry-run] $(q "${PSQL[@]}" -c "$gen")| $(q "${PSQL[@]}")> $out"
    return 0
  fi
  "${PSQL[@]}" -c "$gen" | "${PSQL[@]}" > "$out"
}

compare_counts() { # compare_counts BEFORE AFTER -> notes on stdout; rc 1 on shrink/missing
  awk -F'|' '
    NR==FNR { before[$1] = $2; next }
    { after[$1] = $2 }
    END {
      bad = 0
      for (t in before) {
        if (!(t in after))                 { printf "table %s missing after enable; ", t; bad = 1 }
        else if (after[t]+0 < before[t]+0) { printf "table %s shrank %s->%s; ", t, before[t], after[t]; bad = 1 }
        else if (after[t]+0 > before[t]+0) { printf "table %s grew %s->%s (allowed); ", t, before[t], after[t] }
      }
      exit bad
    }' "$1" "$2"
}

changelog_count() { # changelog_count SCHEMA -> row count, -1 when the table is absent, or DRY
  local s=$1 exists
  exists=$(psql_val "SELECT count(*) FROM information_schema.tables WHERE table_schema='$s' AND table_name='databasechangelog'") || return 1
  if [ "$exists" = DRY ]; then printf 'DRY'; return 0; fi
  if [ "$exists" = 0 ]; then printf '%s' -1; return 0; fi
  psql_val "SELECT count(*) FROM ${s}.databasechangelog"
}

# --- JSON construction (python3 — no string concatenation, F-29) -------------
direct_enable_body() { # _tenant 2.0 body for MODE=direct
  python3 - "$MODULE_FROM" "$MODULE_TO" "$TENANT_PARAMETERS" <<'PY'
import json, sys
mf, mt, tp = sys.argv[1], sys.argv[2], sys.argv[3]
body = {}
if mf:
    body["module_from"] = mf
body["module_to"] = mt
body["purge"] = False
if tp:
    body["parameters"] = json.loads(tp)
print(json.dumps(body))
PY
}

install_body() { # install_body MODULE_ID ACTION — Okapi install API body
  python3 -c 'import json, sys; print(json.dumps([{"id": sys.argv[1], "action": sys.argv[2]}]))' "$1" "$2"
}

tenant_parameters_query() { # -> url-encoded tenantParameters value ('' when unset)
  [ -n "$TENANT_PARAMETERS" ] || { printf ''; return 0; }
  python3 - "$TENANT_PARAMETERS" <<'PY'
import json, sys, urllib.parse
tp = json.loads(sys.argv[1])
print(urllib.parse.quote(",".join(
    "%s=%s" % (e["key"], e.get("value", "")) for e in tp), safe=''))
PY
}

routing_lists() { # routing_lists MODULES_JSON MODULE_ID -> rc 0 when listed
  python3 -c 'import json, sys
ids = [m.get("id") for m in json.load(open(sys.argv[1]))]
sys.exit(0 if sys.argv[2] in ids else 1)' "$1" "$2"
}

okapi_modules() { # okapi_modules TENANT OUTFILE -> prints status or DRY
  http_code "$2" GET "$OKAPI_URL/_/proxy/tenants/$1/modules" \
    -H "X-Okapi-Token: $OKAPI_TOKEN"
}

# Per-tenant smoke: read-only, tenant-neutral, pinned statuses. NEVER
# getNextNumber (it consumes a number — runbook 3.4).  id|path|expected
SMOKE_PROBES=(
  'numgen-list|/servint/numberGenerators?perPage=1|200'
  'settings-list|/servint/settings/appSettings?perPage=1|200'
)

# --- manual recovery command (F-35 — fully resolved, with the token header) --
# $OKAPI_TOKEN is left as a shell expansion ON PURPOSE: the command runs
# verbatim in any shell where the token is exported, and the secret itself
# never lands in the ledger.
manual_rollback_cmd() { # manual_rollback_cmd TENANT
  local T=$1
  local base="${OKAPI_URL:-\$OKAPI_URL}"
  if [ -n "$MODULE_FROM" ]; then
    echo "manual completion per runbook Phase 6.1 (NEVER purge; export OKAPI_TOKEN first): curl --connect-timeout $CURL_CONNECT_TIMEOUT --max-time $CURL_MAX_TIME_INSTALL -sS -w '%{http_code}' -X POST '$base/_/proxy/tenants/$T/install?deploy=true' -H 'Content-Type: application/json' -H \"X-Okapi-Token: \$OKAPI_TOKEN\" -d '$(install_body "$MODULE_FROM" enable)' && curl --connect-timeout $CURL_CONNECT_TIMEOUT --max-time $CURL_MAX_TIME -sS -H \"X-Okapi-Token: \$OKAPI_TOKEN\" '$base/_/proxy/tenants/$T/modules'   # verify: lists $MODULE_FROM, not $MODULE_TO"
  else
    echo "manual completion (fresh tenant — no legacy id to re-enable; NEVER purge; export OKAPI_TOKEN first): curl --connect-timeout $CURL_CONNECT_TIMEOUT --max-time $CURL_MAX_TIME_INSTALL -sS -w '%{http_code}' -X POST '$base/_/proxy/tenants/$T/install' -H 'Content-Type: application/json' -H \"X-Okapi-Token: \$OKAPI_TOKEN\" -d '$(install_body "$MODULE_TO" disable)' && curl --connect-timeout $CURL_CONNECT_TIMEOUT --max-time $CURL_MAX_TIME -sS -H \"X-Okapi-Token: \$OKAPI_TOKEN\" '$base/_/proxy/tenants/$T/modules'   # verify: does not list $MODULE_TO"
  fi
}

# --- per-tenant pipeline -----------------------------------------------------
FAIL_PHASE='' FAIL_DETAIL='' NOTES='' HARD_STOP=false ENABLED=false
ROUTING_STATE='' CHANGELOG_BEFORE=''

note() { NOTES="${NOTES:+$NOTES; }$1"; }

enable_direct() { # enable_direct TENANT SCHEMA TDIR -> rc 0 enabled / 1 fail
  local T=$1 S=$2 TDIR=$3 body code
  body=$(direct_enable_body)
  local hdrs=(-H "X-Okapi-Tenant: $T" -H "X-Okapi-Token: $OKAPI_TOKEN"
              -H 'Content-Type: application/json')
  if [ -n "$OKAPI_URL" ]; then hdrs+=(-H "X-Okapi-Url: $OKAPI_URL"); fi
  code=$(http_code "$TDIR/enable.json" POST "$PORT_URL/_/tenant" "${hdrs[@]}" \
           --data "$body" --max-time "$CURL_MAX_TIME_INSTALL") || code=curl-error
  if [ "$code" = DRY ] || [ "$code" = 204 ]; then
    ENABLED=true
    if [ "$code" = 204 ]; then say "  [pass] enable: POST /_/tenant ($MODULE_TO) -> 204"; fi
    return 0
  fi
  if [ "$code" = curl-error ] || [ "$code" = 000 ]; then
    # F-29: transport error — reconcile against actual state before classifying
    local now
    now=$(changelog_count "$S") || now=unknown
    if [ "$now" != unknown ] && [ "$now" != "$CHANGELOG_BEFORE" ]; then
      ENABLED=true
      note "enable transport error reconciled: databasechangelog $CHANGELOG_BEFORE -> $now rows — the call landed"
      say "  [warn] enable: transport error, reconciled as delivered (changelog $CHANGELOG_BEFORE -> $now)"
      return 0
    fi
    ENABLED=true      # conservative: delivery unproven either way — rollback runs
    FAIL_PHASE=enable-ambiguous
    FAIL_DETAIL="transport error on POST $PORT_URL/_/tenant and databasechangelog unchanged (count $CHANGELOG_BEFORE, now ${now}) — delivery cannot be proven; treated as possibly-enabled so rollback runs"
    return 1
  fi
  FAIL_PHASE=enable
  FAIL_DETAIL="POST $PORT_URL/_/tenant expected HTTP 204, got $code; response: $(head -c 200 "$TDIR/enable.json" 2>/dev/null | tr '\n' ' ')"
  return 1
}

enable_okapi() { # enable_okapi TENANT TDIR -> rc 0 enabled+routed / 1 fail
  local T=$1 TDIR=$2 body url code tpq
  body=$(install_body "$MODULE_TO" enable)
  url="$OKAPI_URL/_/proxy/tenants/$T/install?deploy=true"
  tpq=$(tenant_parameters_query)
  if [ -n "$tpq" ]; then url+="&tenantParameters=$tpq"; fi
  code=$(http_code "$TDIR/enable.json" POST "$url" \
           -H 'Content-Type: application/json' -H "X-Okapi-Token: $OKAPI_TOKEN" \
           --data "$body" --max-time "$CURL_MAX_TIME_INSTALL") || code=curl-error
  if [ "$code" = DRY ]; then
    ENABLED=true
  elif [ "$code" = 200 ]; then
    ENABLED=true
    say "  [pass] enable: Okapi install ($MODULE_TO) -> 200"
  elif [ "$code" = curl-error ] || [ "$code" = 000 ]; then
    # F-29: transport error — reconcile against Okapi's actual module state
    local rcode
    rcode=$(okapi_modules "$T" "$TDIR/okapi-modules-reconcile.json") || rcode=curl-error
    if [ "$rcode" = 200 ] && routing_lists "$TDIR/okapi-modules-reconcile.json" "$MODULE_TO"; then
      ENABLED=true
      note "enable transport error reconciled: Okapi lists $MODULE_TO for $T — the install landed"
      say "  [warn] enable: transport error, reconciled as delivered (Okapi lists $MODULE_TO)"
    elif [ "$rcode" = 200 ]; then
      ENABLED=false
      FAIL_PHASE=enable
      FAIL_DETAIL="transport error on Okapi install and /_/proxy/tenants/$T/modules does NOT list $MODULE_TO — enable classified as not delivered; legacy remains authoritative"
      return 1
    else
      ENABLED=true    # conservative: cannot reconcile — rollback runs
      FAIL_PHASE=enable-ambiguous
      FAIL_DETAIL="transport error on Okapi install and the reconciliation read of /_/proxy/tenants/$T/modules failed ($rcode) — delivery cannot be proven; treated as possibly-enabled so rollback runs"
      return 1
    fi
  else
    FAIL_PHASE=enable
    FAIL_DETAIL="POST $url expected HTTP 200, got $code; response: $(head -c 200 "$TDIR/enable.json" 2>/dev/null | tr '\n' ' ')"
    return 1
  fi

  # F-27: the cutover claim is checked — Okapi must actually route the tenant
  code=$(okapi_modules "$T" "$TDIR/okapi-modules.json") || code=curl-error
  if [ "$code" = DRY ]; then
    ROUTING_STATE="dry-run: routing verification would require /_/proxy/tenants/$T/modules to list $MODULE_TO"
    echo "[dry-run] routing verification: GET $OKAPI_URL/_/proxy/tenants/$T/modules must list $MODULE_TO"
    return 0
  fi
  if [ "$code" = 200 ] && routing_lists "$TDIR/okapi-modules.json" "$MODULE_TO"; then
    ROUTING_STATE="okapi routing verified: /_/proxy/tenants/$T/modules lists $MODULE_TO"
    say "  [pass] routing: Okapi lists $MODULE_TO for $T"
    return 0
  fi
  FAIL_PHASE=routing-verify
  FAIL_DETAIL="Okapi install answered 200 but /_/proxy/tenants/$T/modules (HTTP $code) does not list $MODULE_TO (evidence: $TDIR/okapi-modules.json) — the tenant is NOT cut over"
  return 1
}

process_tenant() { # process_tenant TENANT -> rc 0 pass / 1 fail (FAIL_* set)
  local T=$1
  local S="${T}_mod_service_interaction"
  local TDIR="$RUN_DIR/$T"
  FAIL_PHASE='' FAIL_DETAIL='' NOTES='' HARD_STOP=false ENABLED=false
  ROUTING_STATE='' CHANGELOG_BEFORE=''
  say "--- tenant $T (schema $S) ---"
  if ! is_dry; then mkdir "$TDIR"; fi

  # (a) wire pre-capture (optional; the decision is always recorded)
  if [ -n "$LEGACY_URL" ] && [ -n "$PRE_PROBES" ]; then
    local cap=(env "LEGACY_URL=$LEGACY_URL" "TENANT=$T" "OUT_DIR=$TDIR"
               "PROBES=$PRE_PROBES" "$SCRIPT_DIR/capture.sh" legacy pre)
    if is_dry; then
      echo "[dry-run] $(q "${cap[@]}")"
      note "pre-capture: dry-run only"
    elif "${cap[@]}"; then
      say "  [pass] pre-capture -> $TDIR/pre"
      note "pre-capture retained in $TDIR/pre"
    else
      FAIL_PHASE=pre-capture
      FAIL_DETAIL="capture.sh legacy failed for probe list $PRE_PROBES (evidence: $TDIR/pre); tenant was NOT enabled"
      return 1
    fi
  else
    say "  [skip] pre-capture (LEGACY_URL/PRE_PROBES not configured) — recorded"
    note "pre-capture skipped: LEGACY_URL/PRE_PROBES not configured"
  fi

  # (b) catalog + count + changelog pre-snapshot; adopted-vs-fresh decision
  local adopted=true exists
  exists=$(psql_val "SELECT count(*) FROM pg_namespace WHERE nspname='$S'") || {
    FAIL_PHASE=pre-snapshot
    FAIL_DETAIL="psql could not check existence of schema $S; tenant was NOT enabled"
    return 1
  }
  if [ "$exists" = DRY ]; then
    CHANGELOG_BEFORE=DRY
    note "dry-run: assuming adopted tenant with pre-state"
  elif [ "$exists" = 0 ]; then
    adopted=false
    CHANGELOG_BEFORE=-1
    say "  [note] schema $S absent — fresh tenant, no pre-state to capture"
    note "fresh tenant: schema absent pre-enable; catalog/count comparison skipped"
  else
    CHANGELOG_BEFORE=$(changelog_count "$S") || {
      FAIL_PHASE=pre-snapshot
      FAIL_DETAIL="changelog pre-count failed for $S; tenant was NOT enabled"
      return 1
    }
  fi
  if $adopted; then
    psql_to "$TDIR/catalog-before.txt" "$(catalog_sql "$S")" || {
      FAIL_PHASE=pre-snapshot
      FAIL_DETAIL="catalog pre-snapshot failed for $S; tenant was NOT enabled"
      return 1
    }
    counts_snapshot "$S" "$TDIR/counts-before.txt" || {
      FAIL_PHASE=pre-snapshot
      FAIL_DETAIL="row-count pre-snapshot failed for $S; tenant was NOT enabled"
      return 1
    }
    if ! is_dry && [ ! -s "$TDIR/catalog-before.txt" ]; then
      adopted=false
      note "schema exists but holds no business tables — treated as fresh; comparison skipped"
    fi
    if ! is_dry && $adopted; then say "  [pass] pre-snapshot -> $TDIR/{catalog,counts}-before.txt"; fi
  fi

  # (c)+(d) enable per MODE (F-27), with routing verification in okapi mode
  if [ "$MODE" = okapi ]; then
    enable_okapi "$T" "$TDIR" || return 1
  else
    enable_direct "$T" "$S" "$TDIR" || return 1
  fi

  # (e) post snapshot + comparison + changelog exectype check (runbook 3.3)
  psql_to "$TDIR/catalog-after.txt" "$(catalog_sql "$S")" || {
    FAIL_PHASE=post-verify
    FAIL_DETAIL="catalog post-snapshot failed for $S after enable"
    HARD_STOP=true
    return 1
  }
  counts_snapshot "$S" "$TDIR/counts-after.txt" || {
    FAIL_PHASE=post-verify
    FAIL_DETAIL="row-count post-snapshot failed for $S after enable"
    HARD_STOP=true
    return 1
  }
  if $adopted && ! is_dry; then
    # catalog.diff is evidence either way — an empty file proves the catalog
    # was compared and unchanged; it is hashed into the ledger, never deleted
    if ! diff "$TDIR/catalog-before.txt" "$TDIR/catalog-after.txt" > "$TDIR/catalog.diff"; then
      HARD_STOP=true
      FAIL_PHASE=post-verify-catalog
      FAIL_DETAIL="catalog changed for adopted schema $S (see $TDIR/catalog.diff) — systemic-defect signal (runbook 3.3)"
      return 1
    fi
    local cmp
    if ! cmp=$(compare_counts "$TDIR/counts-before.txt" "$TDIR/counts-after.txt"); then
      HARD_STOP=true
      FAIL_PHASE=post-verify-counts
      FAIL_DETAIL="business row counts regressed in $S: ${cmp:-see $TDIR/counts-*.txt}"
      return 1
    fi
    if [ -n "$cmp" ]; then note "counts: $cmp"; else note "catalog and counts unchanged"; fi
    say "  [pass] post-verify: catalog unchanged, no row-count regression"
  elif $adopted; then
    echo "[dry-run] diff $TDIR/catalog-before.txt $TDIR/catalog-after.txt > $TDIR/catalog.diff   # must be empty; the file is kept as evidence"
    echo "[dry-run] row-count comparison: per-table after >= before (any shrink = FAIL)"
  fi
  local mix executed markran
  mix=$(psql_val "SELECT count(*) FILTER (WHERE exectype='EXECUTED') || '|' || count(*) FILTER (WHERE exectype='MARK_RAN') FROM ${S}.databasechangelog") || {
    HARD_STOP=true
    FAIL_PHASE=post-verify-changelog
    FAIL_DETAIL="cannot read ${S}.databasechangelog after enable"
    return 1
  }
  if [ "$mix" != DRY ]; then
    executed=${mix%%|*}
    markran=${mix##*|}
    if $adopted; then
      if [ "$executed" != 0 ] || [ "$markran" = 0 ]; then
        HARD_STOP=true
        FAIL_PHASE=post-verify-changelog
        FAIL_DETAIL="adopted tenant: databasechangelog EXECUTED=$executed MARK_RAN=$markran (expected EXECUTED=0, MARK_RAN>0 — a fired precondition means DDL ran against business tables; runbook 3.3)"
        return 1
      fi
    elif [ "$executed" = 0 ] && [ "$markran" = 0 ]; then
      HARD_STOP=true
      FAIL_PHASE=post-verify-changelog
      FAIL_DETAIL="fresh tenant: databasechangelog is empty after enable — migration did not run"
      return 1
    fi
    say "  [pass] changelog: EXECUTED=$executed MARK_RAN=$markran"
    note "changelog EXECUTED=$executed MARK_RAN=$markran"
  fi

  # (f) smoke — fixed read-only probes, pinned statuses; THROUGH Okapi in
  # okapi mode (routing proof), module-direct in direct mode
  local smoke_base
  if [ "$MODE" = okapi ]; then smoke_base=$OKAPI_URL; else smoke_base=$PORT_URL; fi
  local probe id path expect code passed=0
  for probe in "${SMOKE_PROBES[@]}"; do
    IFS='|' read -r id path expect <<< "$probe"
    code=$(http_code "$TDIR/smoke-$id.json" GET "$smoke_base$path" \
             -H "X-Okapi-Tenant: $T" -H "X-Okapi-Token: $OKAPI_TOKEN") || code=curl-error
    if [ "$code" = DRY ] || [ "$code" = "$expect" ]; then
      passed=$((passed+1))
      if [ "$code" != DRY ]; then say "  [pass] smoke $id: GET $path -> $code (via $MODE)"; fi
    else
      FAIL_PHASE=smoke
      FAIL_DETAIL="probe $id: GET $smoke_base$path expected HTTP $expect, got $code (response: $TDIR/smoke-$id.json)"
      return 1
    fi
  done
  note "smoke $passed/${#SMOKE_PROBES[@]} pass via $MODE"
  return 0
}

# --- automatic per-tenant rollback (runbook Phase 6) -------------------------
ROLLBACK_DETAIL=''
rollback_tenant() { # rollback_tenant TENANT — sets ROLLBACK_DETAIL; failed rollback sets HARD_STOP (F-29)
  local T=$1
  local manual
  manual=$(manual_rollback_cmd "$T")
  if ! $ENABLED; then
    # Enable never took effect — legacy is still authoritative; the port
    # never writes tenant_changelog, so rollback = not proceeding (runbook 3.2).
    ROLLBACK_DETAIL="no automatic action needed: enable did not take effect, legacy remains authoritative; $manual"
    say "  [note] rollback: enable did not take effect — nothing to undo"
    return 0
  fi
  local code
  if [ "$MODE" = okapi ]; then
    # inverse Okapi transition: re-enable the legacy id (upgrade wave) or
    # disable MODULE_TO (fresh tenant), then RE-VERIFY routing (F-27)
    local rb_id rb_action verify_expect
    if [ -n "$MODULE_FROM" ]; then
      rb_id=$MODULE_FROM rb_action=enable verify_expect=present
    else
      rb_id=$MODULE_TO rb_action=disable verify_expect=absent
    fi
    code=$(http_code "$RUN_DIR/$T/rollback-install.json" POST \
             "$OKAPI_URL/_/proxy/tenants/$T/install?deploy=true" \
             -H 'Content-Type: application/json' -H "X-Okapi-Token: $OKAPI_TOKEN" \
             --data "$(install_body "$rb_id" "$rb_action")" \
             --max-time "$CURL_MAX_TIME_INSTALL") || code=curl-error
    if [ "$code" != DRY ] && [ "$code" != 200 ]; then
      HARD_STOP=true
      ROLLBACK_DETAIL="automatic Okapi rollback FAILED (install $rb_action $rb_id -> HTTP $code, response: $RUN_DIR/$T/rollback-install.json) — routing state UNKNOWN, run aborts; ESCALATE and complete manually; $manual"
      warn "  [fail] rollback: Okapi install $rb_action $rb_id -> $code (HARD STOP — escalate)"
      return 0
    fi
    local rcode listed
    rcode=$(okapi_modules "$T" "$RUN_DIR/$T/rollback-modules.json") || rcode=curl-error
    if [ "$code" = DRY ]; then
      ROLLBACK_DETAIL="dry-run: rollback would install $rb_action $rb_id through Okapi, then verify /_/proxy/tenants/$T/modules shows $rb_id $verify_expect; $manual"
      return 0
    fi
    if [ "$rcode" = 200 ]; then
      if routing_lists "$RUN_DIR/$T/rollback-modules.json" "$rb_id"; then listed=present; else listed=absent; fi
      if [ "$listed" = "$verify_expect" ]; then
        ROLLBACK_DETAIL="automatic Okapi rollback OK: install $rb_action $rb_id -> 200; routing re-verified ($rb_id $listed as expected); legacy data untouched — the port never writes tenant_changelog (runbook Phase 6); $manual"
        say "  [pass] rollback: Okapi $rb_action $rb_id + routing re-verified"
        return 0
      fi
    fi
    HARD_STOP=true
    ROLLBACK_DETAIL="automatic Okapi rollback UNVERIFIED: install $rb_action $rb_id -> 200 but the routing re-verify failed (HTTP $rcode, $rb_id expected $verify_expect; evidence: $RUN_DIR/$T/rollback-modules.json) — run aborts; ESCALATE; $manual"
    warn "  [fail] rollback: routing re-verify failed (HARD STOP — escalate)"
    return 0
  fi
  # MODE=direct: undo the module-direct enable with the _tenant 2.0 disable
  code=$(http_code "$RUN_DIR/$T/rollback-disable.json" POST "$PORT_URL/_/tenant" \
           -H "X-Okapi-Tenant: $T" -H "X-Okapi-Token: $OKAPI_TOKEN" \
           -H 'Content-Type: application/json' \
           --data "$(python3 -c 'import json,sys; print(json.dumps({"module_from": sys.argv[1], "purge": False}))' "$MODULE_TO")") || code=curl-error
  if [ "$code" = DRY ] || [ "$code" = 204 ]; then
    ROLLBACK_DETAIL="automatic port-side _tenant disable OK (module_from=$MODULE_TO, purge=false -> HTTP $code); legacy data untouched — the port never writes tenant_changelog (runbook Phase 6); $manual"
    say "  [pass] rollback: port-side _tenant disable -> $code"
  else
    HARD_STOP=true
    ROLLBACK_DETAIL="automatic port-side _tenant disable FAILED (HTTP $code, response: $RUN_DIR/$T/rollback-disable.json) — run aborts; ESCALATE before any further tenant; $manual"
    warn "  [fail] rollback: port-side _tenant disable -> $code (HARD STOP — escalate)"
  fi
}

# --- preflight (once per run) ------------------------------------------------
PRE_DETAIL=''
preflight() {
  local one code
  one=$(psql_val 'SELECT 1') || { PRE_DETAIL="psql cannot reach $PGHOST:$PGPORT/$PGDATABASE as $PGUSER"; return 1; }
  if [ "$one" != DRY ] && [ "$one" != 1 ]; then
    PRE_DETAIL="unexpected psql connectivity result '$one'"; return 1
  fi
  code=$(http_code "${RUN_DIR}/health.json" GET "$PORT_URL/admin/health") || code=curl-error
  if [ "$code" != DRY ] && [ "$code" != 200 ]; then
    PRE_DETAIL="GET $PORT_URL/admin/health expected HTTP 200, got $code — fix the port deployment before any tenant (runbook 0.4)"
    return 1
  fi
  if [ "$MODE" = okapi ]; then
    code=$(http_code "${RUN_DIR}/okapi-tenants.json" GET "$OKAPI_URL/_/proxy/tenants" \
             -H "X-Okapi-Token: $OKAPI_TOKEN") || code=curl-error
    if [ "$code" != DRY ] && [ "$code" != 200 ]; then
      PRE_DETAIL="GET $OKAPI_URL/_/proxy/tenants expected HTTP 200, got $code — Okapi unreachable, no cutover possible"
      return 1
    fi
  fi
  if [ "$MODE" = okapi ]; then
    say "  [pass] preflight: psql connectivity + /admin/health + okapi reachability"
  else
    say "  [pass] preflight: psql connectivity + /admin/health"
  fi
}

# --- main --------------------------------------------------------------------
say "wave '$WAVE' run $RUN_ID: ${#TENANTS[@]} tenant(s); MODE=$MODE MODULE_TO=$MODULE_TO${MODULE_FROM:+ MODULE_FROM=$MODULE_FROM} MAX_FAILURES=$MAX_FAILURES DRY_RUN=$DRY_RUN"
say "ledger: $LEDGER (append-only); evidence: $RUN_DIR/<tenant>/ (immutable — a retry gets a new run-id)"
if [ "$MODE" = direct ]; then
  say "MODE=direct: pre-verification pass only — ledger rows will say 'verified-direct', Okapi routing is NOT changed and no tenant is cut over"
fi

if ! preflight; then
  ledger_append '-' preflight FAIL "$PRE_DETAIL" ''
  warn "preflight failed — no tenant attempted: $PRE_DETAIL"
  exit 1
fi
# the run-start row doubles as the ledger append probe (F-29)
ledger_append '-' run-start INFO "mode=$MODE module_to=$MODULE_TO${MODULE_FROM:+ module_from=$MODULE_FROM} tenants=${#TENANTS[@]} run_dir=$RUN_DIR" '' || {
  warn "cannot append to ledger $LEDGER — fix the ledger location before any tenant"
  exit 1
}

FAILURES=0
PASSED=0
idx=0
for T in "${TENANTS[@]}"; do
  idx=$((idx+1))
  if process_tenant "$T"; then
    PASSED=$((PASSED+1))
    if [ "$MODE" = okapi ]; then
      ledger_append "$T" complete PASS "${ROUTING_STATE}${NOTES:+; $NOTES}" "$(evidence_digest "$RUN_DIR/$T")"
    else
      ledger_append "$T" verified-direct PASS "module-direct pre-verification only — NOT a cutover, Okapi routing unchanged${NOTES:+; $NOTES}" "$(evidence_digest "$RUN_DIR/$T")"
    fi
    if [ "$MODE" = okapi ]; then
      say "  [pass] tenant $T complete (routing verified)"
    else
      say "  [pass] tenant $T verified-direct (NOT cut over)"
    fi
    continue
  fi
  FAILURES=$((FAILURES+1))
  warn "  [fail] tenant $T at phase '$FAIL_PHASE': $FAIL_DETAIL"
  rollback_tenant "$T"
  ledger_append "$T" "$FAIL_PHASE" FAIL "$FAIL_DETAIL | rollback: $ROLLBACK_DETAIL${NOTES:+ | $NOTES}" "$(evidence_digest "$RUN_DIR/$T")"
  reason=''
  if $HARD_STOP; then
    reason="hard-stop failure (catalog/count/changelog verification or failed rollback) — aborts immediately regardless of MAX_FAILURES"
  elif [ "$FAILURES" -ge "$MAX_FAILURES" ]; then
    reason="MAX_FAILURES=$MAX_FAILURES reached"
  fi
  if [ -n "$reason" ]; then
    remaining=("${TENANTS[@]:$idx}")
    ledger_append '-' stop-condition ABORT "$reason after tenant $T; not attempted: ${remaining[*]:-none}" ''
    warn "STOP — $reason. Tenants not attempted: ${remaining[*]:-none}"
    warn "wave '$WAVE' run $RUN_ID: passed=$PASSED failed=$FAILURES skipped=${#remaining[@]}"
    exit 1
  fi
done

if is_dry; then
  echo "[dry-run] on any tenant failure after an effective enable, the automatic rollback would run:"
  if [ "$MODE" = okapi ]; then
    echo "[dry-run]   POST $OKAPI_URL/_/proxy/tenants/<tenant>/install?deploy=true with $(install_body "${MODULE_FROM:-$MODULE_TO}" "$([ -n "$MODULE_FROM" ] && echo enable || echo disable)")   # expect 200"
    echo "[dry-run]   then GET $OKAPI_URL/_/proxy/tenants/<tenant>/modules to re-verify routing; a failed rollback HARD-STOPS the run"
  else
    echo "[dry-run]   POST $PORT_URL/_/tenant with {\"module_from\":\"$MODULE_TO\",\"purge\":false}   # expect 204; a failed rollback HARD-STOPS the run"
  fi
  echo "[dry-run]   and the ledger row records the fully resolved manual Phase 6.1 command (token via \$OKAPI_TOKEN)"
fi

say "wave '$WAVE' run $RUN_ID finished: passed=$PASSED failed=$FAILURES skipped=0"
if [ "$FAILURES" -gt 0 ]; then
  warn "at least one tenant failed — see the ledger: $LEDGER"
  exit 1
fi
if [ "$MODE" = direct ]; then
  say "OK — every tenant passed the DIRECT pre-verification; no tenant is cut over (run the okapi-mode wave for that)"
else
  say "OK — every tenant in wave '$WAVE' passed with verified Okapi routing"
fi
