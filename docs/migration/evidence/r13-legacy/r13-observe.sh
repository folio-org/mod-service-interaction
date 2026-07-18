#!/bin/bash
# R13 (remediation plan 2) — legacy oracle observation run.
#
# Runs the populate.sh request sequence in OBSERVATION mode (no status
# assertions — every observed status is recorded) against a freshly enabled
# tenant on the REAL legacy Grails module, then the directed probe rows from
# re-review №2 (F-21 parser table, the starred-legacy f14 rows of probes.tsv,
# and the widget-definition double-POST that resolves D-2's 201-vs-500).
#
# Every response body is retained raw as <OUT>/<id>.json with a manifest row
# id<TAB>method<TAB>status<TAB>content-type<TAB>bytes<TAB>rows<TAB>path in
# <OUT>/observations.tsv (rows = jq length when the body is a JSON array).
#
# Usage: r13-observe.sh <tenant> <out-dir>   (legacy at $BASE, default :8080)
set -euo pipefail
BASE=${BASE:-http://localhost:8080}
T=${1:?tenant}
OUT=${2:?out-dir}
A=11111111-1111-1111-1111-111111111111
B=22222222-2222-2222-2222-222222222222
mkdir -p "$OUT"
MAN="$OUT/observations.tsv"
: > "$MAN"

rec() { # rec ID METHOD PATH USER(A|B|notenant) [BODY] [QUERYFILTER]
  local id=$1 method=$2 path=$3 user=$4 body=${5:-} qfilter=${6:-}
  local args=(-s -S -o "$OUT/$id.json" -D "$OUT/$id.headers" -w '%{http_code}' -X "$method" --max-time 30)
  case "$user" in
    A) args+=(-H "X-Okapi-Tenant: $T" -H "X-Okapi-User-Id: $A" -H "X-Okapi-Token: DUMMY") ;;
    B) args+=(-H "X-Okapi-Tenant: $T" -H "X-Okapi-User-Id: $B" -H "X-Okapi-Token: DUMMY") ;;
    notenant) args+=(-H "X-Okapi-User-Id: $A" -H "X-Okapi-Token: DUMMY") ;;
  esac
  [ -n "$body" ] && args+=(-H 'Content-Type: application/json' --data "$body")
  if [ -n "$qfilter" ]; then
    args+=(-G --data-urlencode "filters=$qfilter" --data-urlencode 'perPage=100' --data-urlencode 'sort=code;asc')
  fi
  local code ctype bytes rows
  code=$(curl "${args[@]}" "$BASE$path" </dev/null) || code=curl-fail
  ctype=$(grep -i '^content-type:' "$OUT/$id.headers" 2>/dev/null | head -1 | tr -d '\r' | cut -d' ' -f2- || true)
  bytes=$(wc -c < "$OUT/$id.json" 2>/dev/null || echo 0)
  rows=$(jq 'if type=="array" then length else "-" end' "$OUT/$id.json" 2>/dev/null || echo "-")
  printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\n' "$id" "$method" "$code" "${ctype:--}" "$bytes" "$rows" "$path" >> "$MAN"
  echo "  $id -> $code (${rows} rows, ${bytes}B)"
}

echo "== enable tenant $T (loadReference+loadSample)"
rec tenant-enable POST /_/tenant A '{"module_to":"mod-service-interaction-4.4.0","parameters":[{"key":"loadReference","value":"true"},{"key":"loadSample","value":"true"}]}'
sleep 5

echo "== populate sequence (observation mode)"
rec provision-user-A GET /servint/dashboard/my-dashboards A
rec provision-user-B GET /servint/dashboard/my-dashboards B
rec dashboard-create POST /servint/dashboard A '{"name":"Migration Board"}'
DASH=$(jq -r '.id // empty' "$OUT/dashboard-create.json")
if [ -z "$DASH" ]; then echo "FATAL: no dashboard id — cannot continue"; exit 1; fi
echo "  DASH=$DASH"
rec dashboard-grants POST "/servint/dashboard/$DASH/users" A \
  '[{"user":{"id":"'"$A"'"},"access":"manage"},{"user":{"id":"'"$B"'"},"access":"view"}]'
rec widget-type-import POST /servint/admin/triggerTypeImport A
if ! grep -qP '^widget-type-import\tPOST\t200' "$MAN"; then
  rec widget-type-import-get GET /servint/admin/triggerTypeImport A
fi
rec widget-types GET '/servint/widgets/types?perPage=100&sort=name%3Basc' A
TYPENAME=$(jq -r '.[0].name' "$OUT/widget-types.json")
TYPEVER=$(jq -r '.[0].typeVersion' "$OUT/widget-types.json")
echo "  first widget type: $TYPENAME@$TYPEVER"
WDBODY='{"name":"m4-def","definitionVersion":"1.0","typeName":"'"$TYPENAME"'","typeVersion":"'"$TYPEVER"'","definition":"{\"baseUrl\":\"/erm/sas\",\"results\":{\"columns\":[{\"name\":\"agreementName\",\"label\":\"Name\"}]}}"}'
rec widget-def-create POST /servint/widgets/definitions A "$WDBODY"
rec widget-def-verify GET '/servint/widgets/definitions?filters=name%3D%3Dm4-def&perPage=10' A
rec widget-def-create-retry POST /servint/widgets/definitions A "$WDBODY"
rec widget-def-verify-2 GET '/servint/widgets/definitions?filters=name%3D%3Dm4-def&perPage=10' A
rec widget-instance-1 POST /servint/widgets/instances A \
  '{"name":"M4 widget one","definitionName":"m4-def","definitionVersion":"1.0","configuration":"{\"filters\":[]}","owner":{"id":"'"$DASH"'"}}'
rec widget-instance-2 POST /servint/widgets/instances A \
  '{"name":"M4 widget two","definitionName":"m4-def","definitionVersion":"1.0","configuration":"{\"filters\":[{\"name\":\"x\"}]}","owner":{"id":"'"$DASH"'"}}'
rec numgen-create POST /servint/numberGenerators A '{"code":"m4gen","name":"M4 Generator"}'
GEN=$(jq -r '.id // empty' "$OUT/numgen-create.json")
[ -n "$GEN" ] || { echo "FATAL: no generator id"; exit 1; }
rec numgen-seq-plain POST /servint/numberGeneratorSequences A \
  '{"owner":{"id":"'"$GEN"'"},"code":"plain","name":"plain","prefix":"m4-","format":"00000","nextValue":1}'
rec numgen-seq-checked POST /servint/numberGeneratorSequences A \
  '{"owner":{"id":"'"$GEN"'"},"code":"checked","name":"checked","format":"000000000","checkDigitAlgo":{"value":"ean13"},"outputTemplate":"0698${generated_number}${checksum}","nextValue":1}'
for i in 1 2 3; do
  rec "numgen-consume-plain-$i" GET '/servint/numberGenerators/getNextNumber?generator=m4gen&sequence=plain' A
done
rec numgen-consume-checked GET '/servint/numberGenerators/getNextNumber?generator=m4gen&sequence=checked' A
rec numgen-list GET '/servint/numberGenerators?sort=code%3Basc&perPage=100' A
SEEDGEN=$(jq -r '.[0].code' "$OUT/numgen-list.json")
SEEDSEQ=$(jq -r '.[0].sequences[0].code' "$OUT/numgen-list.json")
echo "  seeded generator/sequence: $SEEDGEN/$SEEDSEQ"
rec numgen-consume-seeded GET "/servint/numberGenerators/getNextNumber?generator=$SEEDGEN&sequence=$SEEDSEQ" A
rec setting-alpha POST /servint/settings/appSettings A \
  '{"section":"m4","key":"alpha","settingType":"String","value":"one"}'
rec setting-secret POST /servint/settings/appSettings A \
  '{"section":"m4","key":"secret","settingType":"Password","value":"s3cr3t","hidden":true}'
rec refdata-category POST /servint/refdata A \
  '{"desc":"M4.Custom","values":[{"label":"First"},{"label":"Second","value":"second_custom"}]}'
rec attestation-token GET /servint/attestation/token A

echo "== directed probes: F-21 parser rows (numberGeneratorSequences listing)"
rec f21-invalid-boolean GET /servint/numberGeneratorSequences A '' 'enabled==notabool'
rec f21-empty-rhs GET /servint/numberGeneratorSequences A '' 'nextValue=='
rec f21-escaped-literal GET /servint/numberGeneratorSequences A '' 'code==pl\ain'
rec f21-unbalanced-paren GET /servint/numberGeneratorSequences A '' '(code==plain'
rec f21-gt-notanumber GET /servint/numberGeneratorSequences A '' 'nextValue>notanumber'
rec f21-10k-filter GET /servint/numberGeneratorSequences A '' "$(printf 'a%.0s' $(seq 1 10000))"
rec f21-baseline-plain GET /servint/numberGeneratorSequences A '' 'code==plain'

echo "== directed probes: starred-legacy f14 rows from probes.tsv"
rec f14-malformed-json POST /servint/numberGenerators A '{"code":"broken"'
rec f14-missing-tenant GET '/servint/numberGenerators?perPage=10' notenant
rec f14-stats-notabool GET '/servint/numberGenerators?stats=notabool&perPage=100&sort=code%3Basc' A
rec f14-perpage-nonnumeric GET '/servint/numberGenerators?perPage=abc&sort=code%3Basc' A
rec f14-duplicate-code POST /servint/numberGenerators A '{"code":"m4gen","name":"duplicate code probe"}'

echo "== DONE — manifest:"
cat "$MAN"
