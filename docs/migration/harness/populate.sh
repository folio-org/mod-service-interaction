#!/bin/bash
# Populate tenant $TENANT with rows in EVERY port-owned table through the
# module's REST API (review F-11 remediation: every request asserts its
# expected status and every extracted id is checked non-null; a mismatch
# aborts non-zero naming the failing step).
#
# Designed to run ONCE against a freshly-enabled tenant on the LEGACY module
# (the M4 rehearsal baseline). SIDE=port supported for fresh-DDL rehearsals.
# Per-side expectations (R13 legacy evidence, docs/migration/evidence/r13-legacy/):
# dashboard create legacy 200 vs port 201 (D-27), widget-definition POST
# legacy 201 vs port 405 (D-2: the port declares no local-definition POST).
#
# Usage:   populate.sh
# Env:     SIDE       legacy (default) | port — selects base URL + D-2 expectation
#          LEGACY_URL (default http://localhost:8080)
#          PORT_URL   (default http://localhost:8081)
#          BASE_URL   explicit override of the selected base URL
#          TENANT     (default m4proof)
#          OUT_DIR    (default $PWD/captures) — evidence in $OUT_DIR/populate-<side>
#          USER_A / USER_B (default M4 rehearsal user ids)
set -euo pipefail

SIDE=${SIDE:-legacy}
LEGACY_URL=${LEGACY_URL:-http://localhost:8080}
PORT_URL=${PORT_URL:-http://localhost:8081}
TENANT=${TENANT:-m4proof}
OUT_DIR=${OUT_DIR:-$PWD/captures}
USER_A=${USER_A:-11111111-1111-1111-1111-111111111111}
USER_B=${USER_B:-22222222-2222-2222-2222-222222222222}

case "$SIDE" in
  legacy) BASE=$LEGACY_URL ;;
  port)   BASE=$PORT_URL ;;
  *) echo "populate: SIDE must be legacy or port (got '$SIDE')" >&2; exit 2 ;;
esac
BASE=${BASE_URL:-$BASE}
POP="$OUT_DIR/populate-$SIDE"
mkdir -p "$POP"

STEP=""
fail() { echo "POPULATE FAILED at step '$STEP': $*" >&2; exit 1; }

req() { # req STEP EXPECT METHOD PATH USERID [BODY] — response body -> $POP/$STEP.json
  STEP=$1
  local expect=$2 method=$3 path=$4 user=$5 body=${6:-}
  local args=(-s -S -o "$POP/$STEP.json" -w '%{http_code}' -X "$method" "$BASE$path"
              -H "X-Okapi-Tenant: $TENANT" -H "X-Okapi-User-Id: $user" -H "X-Okapi-Token: DUMMY")
  if [ -n "$body" ]; then
    args+=(-H 'Content-Type: application/json' --data "$body")
  fi
  local code
  code=$(curl "${args[@]}" </dev/null) || fail "curl could not reach $BASE$path"
  if [ "$code" != "$expect" ]; then
    fail "$method $path expected HTTP $expect, got $code — response: $(head -c 300 "$POP/$STEP.json")"
  fi
  echo "  [pass] $STEP: $method $path -> $code"
}

field() { # field STEP JQ_FILTER — extract from a captured response, require non-null/non-empty
  local step=$1 filter=$2 v
  v=$(jq -r "$filter" "$POP/$step.json" 2>/dev/null) || fail "response of '$step' is not JSON"
  if [ -z "$v" ] || [ "$v" = null ]; then
    fail "response of '$step' has null/empty $filter — cannot continue"
  fi
  printf '%s\n' "$v"
}

echo "== populate $SIDE @ $BASE, tenant=$TENANT, evidence -> $POP"

echo "== provision users via my-dashboards"
req provision-user-A 200 GET /servint/dashboard/my-dashboards "$USER_A"
req provision-user-B 200 GET /servint/dashboard/my-dashboards "$USER_B"

echo "== extra dashboard owned by A"
# Legacy answers 200 from its bespoke respond() call; the port's governed
# contract answers 201 (R13 run1/run2 evidence, review finding F-20 / D-27).
if [ "$SIDE" = legacy ]; then DASH_EXPECT=200; else DASH_EXPECT=201; fi
req dashboard-create "$DASH_EXPECT" POST /servint/dashboard "$USER_A" '{"name":"Migration Board"}'
DASH=$(field dashboard-create '.id')
echo "  DASH=$DASH"

echo "== grants: A manage, B view on Migration Board"
req dashboard-grants 200 POST "/servint/dashboard/$DASH/users" "$USER_A" \
  '[{"user":{"id":"'"$USER_A"'"},"access":"manage"},{"user":{"id":"'"$USER_B"'"},"access":"view"}]'

echo "== widget type import"
STEP=widget-type-import
imported=""
for m in POST GET; do
  code=$(curl -s -S -o "$POP/widget-type-import.json" -w '%{http_code}' -X "$m" \
      "$BASE/servint/admin/triggerTypeImport" \
      -H "X-Okapi-Tenant: $TENANT" -H "X-Okapi-User-Id: $USER_A" -H "X-Okapi-Token: DUMMY" \
      </dev/null) || fail "curl could not reach $BASE/servint/admin/triggerTypeImport"
  if [ "$code" = 200 ]; then imported=$m; break; fi
done
if [ -z "$imported" ]; then
  fail "triggerTypeImport returned no 200 via POST or GET (last code: $code)"
fi
echo "  [pass] widget-type-import: $imported -> 200"

req widget-types 200 GET "/servint/widgets/types?perPage=100&sort=name%3Basc" "$USER_A"
TYPENAME=$(field widget-types '.[0].name')
TYPEVER=$(field widget-types '.[0].typeVersion')
echo "  first widget type: $TYPENAME@$TYPEVER"

echo "== widget definition (D-2: legacy creates with 201; the port declares no local-definition POST — 405)"
if [ "$SIDE" = port ]; then
  # D-2 (corrected by the R12 certification run): the governed spec never
  # declared local-definition creation, so the port answers 405 and no row
  # exists to read back or hang instances off — those steps only make sense
  # against legacy (or an adopted schema that already carries m4-def).
  req widget-def-create 405 POST /servint/widgets/definitions "$USER_A" \
    '{"name":"m4-def","definitionVersion":"1.0","typeName":"'"$TYPENAME"'","typeVersion":"'"$TYPEVER"'","definition":"{}"}'
  echo "  [skip] widget-def-verify + widget-instance-1/2 on SIDE=port (D-2: no local-definition surface; instances need a federated definition)"
else
  # R13 evidence (run1 + run2): a FRESH create answers 201 with the created
  # body (no id — gson includes:[]); only a DUPLICATE retry 500s while
  # rendering the unique-constraint error (NoSuchMessageException). The old
  # 500 pin here came from an M4 observation of the retry path.
  req widget-def-create 201 POST /servint/widgets/definitions "$USER_A" \
    '{"name":"m4-def","definitionVersion":"1.0","typeName":"'"$TYPENAME"'","typeVersion":"'"$TYPEVER"'","definition":"{\"baseUrl\":\"/erm/sas\",\"results\":{\"columns\":[{\"name\":\"agreementName\",\"label\":\"Name\"}]}}"}'
  # The 201 body carries no id — prove the create by reading it back.
  req widget-def-verify 200 GET "/servint/widgets/definitions?filters=name%3D%3Dm4-def&perPage=10" "$USER_A"
  N=$(field widget-def-verify 'length')
  [ "$N" = 1 ] || fail "expected exactly 1 'm4-def' widget definition after create, found $N"

  echo "== widget instances on Migration Board"
  req widget-instance-1 201 POST /servint/widgets/instances "$USER_A" \
    '{"name":"M4 widget one","definitionName":"m4-def","definitionVersion":"1.0","configuration":"{\"filters\":[]}","owner":{"id":"'"$DASH"'"}}'
  req widget-instance-2 201 POST /servint/widgets/instances "$USER_A" \
    '{"name":"M4 widget two","definitionName":"m4-def","definitionVersion":"1.0","configuration":"{\"filters\":[{\"name\":\"x\"}]}","owner":{"id":"'"$DASH"'"}}'
fi

echo "== custom number generator + sequences"
req numgen-create 201 POST /servint/numberGenerators "$USER_A" '{"code":"m4gen","name":"M4 Generator"}'
GEN=$(field numgen-create '.id')

echo "== M9 R31/R32 discriminator rows (escaped-token + wildcard oracle fixtures)"
req numgen-create-r31esc 201 POST /servint/numberGenerators "$USER_A" '{"code":"alpha\\&&prefix==","name":"r31 raw absorb discriminator"}'
req numgen-create-r32u 201 POST /servint/numberGenerators "$USER_A" '{"code":"ab_cd","name":"r32 underscore discriminator"}'
req numgen-create-r32x 201 POST /servint/numberGenerators "$USER_A" '{"code":"abXcd","name":"r32 single-char discriminator"}'
req numgen-create-r32pct 201 POST /servint/numberGenerators "$USER_A" '{"code":"ab%cd","name":"r32 literal-percent discriminator"}'
req numgen-create-r32d2 201 POST /servint/numberGenerators "$USER_A" '{"code":"ab$2cd","name":"r32 broken-transform positive proof"}'
req numgen-seq-plain 201 POST /servint/numberGeneratorSequences "$USER_A" \
  '{"owner":{"id":"'"$GEN"'"},"code":"plain","name":"plain","prefix":"m4-","format":"00000","nextValue":1}'
req numgen-seq-checked 201 POST /servint/numberGeneratorSequences "$USER_A" \
  '{"owner":{"id":"'"$GEN"'"},"code":"checked","name":"checked","format":"000000000","checkDigitAlgo":{"value":"ean13"},"outputTemplate":"0698${generated_number}${checksum}","nextValue":1}'

echo "== consume numbers (advances nextValue in place)"
for i in 1 2 3; do
  req "numgen-consume-plain-$i" 200 GET "/servint/numberGenerators/getNextNumber?generator=m4gen&sequence=plain" "$USER_A"
  field "numgen-consume-plain-$i" '.nextValue' >/dev/null
done
req numgen-consume-checked 200 GET "/servint/numberGenerators/getNextNumber?generator=m4gen&sequence=checked" "$USER_A"
field numgen-consume-checked '.nextValue' >/dev/null

echo "== consume one number from the first listed (seeded) generator"
req numgen-list 200 GET "/servint/numberGenerators?sort=code%3Basc&perPage=100" "$USER_A"
# First generator that HAS sequences: the M9 R31/R32 discriminator rows above
# are bare generators that sort before the seeded ones; the consume target is
# still the first seeded generator (inventory_accessionNumber), as pre-M9.
SEEDGEN=$(field numgen-list 'first(.[] | select((.sequences | length) > 0)) | .code')
SEEDSEQ=$(field numgen-list 'first(.[] | select((.sequences | length) > 0)) | .sequences[0].code')
echo "  seeded generator/sequence: $SEEDGEN/$SEEDSEQ"
req numgen-consume-seeded 200 GET "/servint/numberGenerators/getNextNumber?generator=$SEEDGEN&sequence=$SEEDSEQ" "$USER_A"
field numgen-consume-seeded '.nextValue' >/dev/null

echo "== app settings"
req setting-alpha 201 POST /servint/settings/appSettings "$USER_A" \
  '{"section":"m4","key":"alpha","settingType":"String","value":"one"}'
req setting-secret 201 POST /servint/settings/appSettings "$USER_A" \
  '{"section":"m4","key":"secret","settingType":"Password","value":"s3cr3t","hidden":true}'

echo "== custom refdata category"
req refdata-category 201 POST /servint/refdata "$USER_A" \
  '{"desc":"M4.Custom","values":[{"label":"First"},{"label":"Second","value":"second_custom"}]}'

echo "== attestation (creates db_key_pair)"
req attestation-token 200 GET /servint/attestation/token "$USER_A"

echo "POPULATE OK — all steps passed; per-step responses retained in $POP"
