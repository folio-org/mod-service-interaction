#!/bin/bash
# R13 directed probe set — the re-review №2 rows, runnable against EITHER side
# of an already-populated tenant (populate.sh must have run first: needs the
# m4gen generator, the plain/checked sequences, and the m4-def definition).
#
# Rows: the six F-21 parser probes (numberGeneratorSequences listing), the
# starred-legacy f14 rows of probes.tsv, and the widget-definition DUPLICATE
# POST (the path the old D-2 500 pin actually came from).
#
# Observation mode: statuses are recorded, never asserted. Raw bodies kept as
# <OUT>/<id>.json; manifest <OUT>/observations.tsv
# (id, method, status, content-type, bytes, rows, path).
#
# Usage: r13-probes.sh <tenant> <out-dir>    Env: BASE (default :8080 legacy)
set -euo pipefail
BASE=${BASE:-http://localhost:8080}
T=${1:?tenant}
OUT=${2:?out-dir}
A=11111111-1111-1111-1111-111111111111
mkdir -p "$OUT"
MAN="$OUT/observations.tsv"
: > "$MAN"

rec() { # rec ID METHOD PATH USER(A|notenant) [BODY] [QUERYFILTER]
  local id=$1 method=$2 path=$3 user=$4 body=${5:-} qfilter=${6:-}
  local args=(-s -S -o "$OUT/$id.json" -D "$OUT/$id.headers" -w '%{http_code}' -X "$method" --max-time 30)
  case "$user" in
    A) args+=(-H "X-Okapi-Tenant: $T" -H "X-Okapi-User-Id: $A" -H "X-Okapi-Token: DUMMY") ;;
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

echo "== F-21 parser rows (numberGeneratorSequences listing) @ $BASE tenant=$T"
rec f21-invalid-boolean GET /servint/numberGeneratorSequences A '' 'enabled==notabool'
rec f21-empty-rhs GET /servint/numberGeneratorSequences A '' 'nextValue=='
rec f21-escaped-literal GET /servint/numberGeneratorSequences A '' 'code==pl\ain'
rec f21-unbalanced-paren GET /servint/numberGeneratorSequences A '' '(code==plain'
rec f21-gt-notanumber GET /servint/numberGeneratorSequences A '' 'nextValue>notanumber'
rec f21-10k-filter GET /servint/numberGeneratorSequences A '' "$(printf 'a%.0s' $(seq 1 10000))"
rec f21-baseline-plain GET /servint/numberGeneratorSequences A '' 'code==plain'

echo "== starred-legacy f14 rows from probes.tsv"
rec f14-malformed-json POST /servint/numberGenerators A '{"code":"broken"'
rec f14-missing-tenant GET '/servint/numberGenerators?perPage=10' notenant
rec f14-stats-notabool GET '/servint/numberGenerators?stats=notabool&perPage=100&sort=code%3Basc' A
rec f14-perpage-nonnumeric GET '/servint/numberGenerators?perPage=abc&sort=code%3Basc' A
rec f14-duplicate-code POST /servint/numberGenerators A '{"code":"m4gen","name":"duplicate code probe"}'

echo "== widget-definition duplicate POST (D-2 retry path)"
TYPENAME=$(curl -s "$BASE/servint/widgets/types?perPage=100&sort=name%3Basc" \
  -H "X-Okapi-Tenant: $T" -H "X-Okapi-User-Id: $A" -H "X-Okapi-Token: DUMMY" </dev/null | jq -r '.[0].name')
TYPEVER=$(curl -s "$BASE/servint/widgets/types?perPage=100&sort=name%3Basc" \
  -H "X-Okapi-Tenant: $T" -H "X-Okapi-User-Id: $A" -H "X-Okapi-Token: DUMMY" </dev/null | jq -r '.[0].typeVersion')
rec widget-def-create-duplicate POST /servint/widgets/definitions A \
  '{"name":"m4-def","definitionVersion":"1.0","typeName":"'"$TYPENAME"'","typeVersion":"'"$TYPEVER"'","definition":"{\"baseUrl\":\"/erm/sas\",\"results\":{\"columns\":[{\"name\":\"agreementName\",\"label\":\"Name\"}]}}"}'

echo "== DONE — manifest:"
cat "$MAN"
