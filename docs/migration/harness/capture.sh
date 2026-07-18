#!/bin/bash
# Evidence-grade wire capture (review F-11 remediation).
#
# Captures every probe in probes.tsv against ONE side (legacy or port),
# asserts each response status against the side's pinned expectation, and
# writes an auditable manifest.json (probe -> request, expected/actual status,
# response file, sha256 of the jq -S normalized body).
#
# Usage:   capture.sh <legacy|port> [OUT_SUBDIR]
# Env:     LEGACY_URL (default http://localhost:8080)
#          PORT_URL   (default http://localhost:8081)
#          TENANT     (default m4proof)
#          OUT_DIR    (default $PWD/captures)  — run dir is $OUT_DIR/<side>
#          USER_A / USER_B (default M4 rehearsal user ids)
#          PROBES     (default probes.tsv next to this script)
# Exit:    0 all pinned expectations held; 1 any probe mismatched (each named
#          on stderr); 2 usage / malformed probe list.
set -euo pipefail

usage() { echo "usage: capture.sh <legacy|port> [OUT_SUBDIR]" >&2; exit 2; }

SIDE=${1:-}
case "$SIDE" in legacy|port) ;; *) usage ;; esac

LEGACY_URL=${LEGACY_URL:-http://localhost:8080}
PORT_URL=${PORT_URL:-http://localhost:8081}
TENANT=${TENANT:-m4proof}
OUT_DIR=${OUT_DIR:-$PWD/captures}
USER_A=${USER_A:-11111111-1111-1111-1111-111111111111}
USER_B=${USER_B:-22222222-2222-2222-2222-222222222222}
SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
PROBES=${PROBES:-$SCRIPT_DIR/probes.tsv}

if [ "$SIDE" = legacy ]; then BASE=$LEGACY_URL; else BASE=$PORT_URL; fi
OUT="$OUT_DIR/${2:-$SIDE}"
mkdir -p "$OUT"
[ -r "$PROBES" ] || { echo "capture: probe list not found: $PROBES" >&2; exit 2; }

ENTRIES="$OUT/.manifest-entries.ndjson"
: > "$ENTRIES"

DASH=""
resolve_dash() {
  if [ -z "$DASH" ]; then
    DASH=$(curl -s "$BASE/servint/dashboard/my-dashboards" \
        -H "X-Okapi-Tenant: $TENANT" -H "X-Okapi-User-Id: $USER_A" -H "X-Okapi-Token: DUMMY" \
        </dev/null \
      | jq -r '.[] | select(.dashboard.name=="Migration Board") | .dashboard.id' 2>/dev/null \
      | head -n1) || DASH=""
    if [ -z "$DASH" ] || [ "$DASH" = null ]; then
      echo "capture: FAIL — cannot resolve dashboard 'Migration Board' for a {DASH} probe path." >&2
      echo "capture: run populate.sh against the legacy module first (see README.md)." >&2
      exit 1
    fi
  fi
}

FAILURES=0
PROBE_COUNT=0

while IFS=$'\t' read -r id method user expect_legacy expect_port path body || [ -n "${id:-}" ]; do
  case "$id" in ''|'#'*) continue ;; esac
  if [ -z "${path:-}" ] || [ "${path#/}" = "$path" ]; then
    echo "capture: malformed probe row (need id, method, user, expect_legacy, expect_port, /path, body): '$id'" >&2
    exit 2
  fi
  PROBE_COUNT=$((PROBE_COUNT+1))

  if [ "$SIDE" = port ]; then expect=$expect_port; else expect=$expect_legacy; fi
  case "$expect" in '*'|[0-9][0-9][0-9]) ;; *)
    echo "capture: probe '$id': invalid expected status '$expect' (want 3-digit code or *)" >&2
    exit 2 ;;
  esac

  case "$path" in *'{DASH}'*) resolve_dash; path=${path//'{DASH}'/$DASH} ;; esac

  headers=(-H "X-Okapi-Token: DUMMY")
  case "$user" in
    A)        headers+=(-H "X-Okapi-Tenant: $TENANT" -H "X-Okapi-User-Id: $USER_A") ;;
    B)        headers+=(-H "X-Okapi-Tenant: $TENANT" -H "X-Okapi-User-Id: $USER_B") ;;
    notenant) headers+=(-H "X-Okapi-User-Id: $USER_A") ;;
    *) echo "capture: probe '$id': unknown user '$user' (want A|B|notenant)" >&2; exit 2 ;;
  esac

  raw="$OUT/$id.raw"
  curl_args=(-s -S -o "$raw" -w '%{http_code}' -X "$method" "$BASE$path" "${headers[@]}")
  if [ -n "${body:-}" ] && [ "$body" != '-' ]; then
    curl_args+=(-H 'Content-Type: application/json' --data "$body")
  fi
  code=$(curl "${curl_args[@]}" </dev/null) || {
    echo "capture: FAIL — probe '$id': curl could not reach $BASE$path" >&2
    exit 1
  }

  norm=jq-S
  if ! jq -S . "$raw" > "$OUT/$id.json" 2>/dev/null; then
    cp "$raw" "$OUT/$id.json"   # non-JSON (or empty) body kept verbatim
    norm=raw
  fi
  rm -f "$raw"
  sha=$(sha256sum "$OUT/$id.json" | cut -d' ' -f1)

  if [ "$expect" = '*' ]; then
    result=recorded
    echo "  [recorded] $id: $method $path -> $code (expectation not pinned yet)"
  elif [ "$code" = "$expect" ]; then
    result=pass
    echo "  [pass]     $id: $method $path -> $code"
  else
    result=fail
    FAILURES=$((FAILURES+1))
    echo "capture: ASSERTION FAILED — probe '$id': $method $path expected HTTP $expect, got $code" >&2
  fi

  jq -n --arg id "$id" --arg method "$method" --arg path "$path" --arg user "$user" \
        --arg expect "$expect" --arg code "$code" --arg file "$id.json" \
        --arg sha "$sha" --arg norm "$norm" --arg body "${body:--}" --arg result "$result" \
        '{id: $id,
          request: {method: $method, path: $path, user: $user,
                    body: (if $body == "-" then null else $body end)},
          expected_status: (if $expect == "*" then "unpinned" else ($expect | tonumber) end),
          actual_status: ($code | tonumber? // $code),
          response_file: $file,
          body_sha256: $sha,
          normalization: $norm,
          result: $result}' >> "$ENTRIES"
done < "$PROBES"

if [ "$PROBE_COUNT" -eq 0 ]; then
  echo "capture: probe list $PROBES contains no probes" >&2
  exit 2
fi

probes_sha=$(sha256sum "$PROBES" | cut -d' ' -f1)
git_rev=$(git -C "$SCRIPT_DIR" rev-parse --short HEAD 2>/dev/null || echo unknown)
jq -s --arg side "$SIDE" --arg base "$BASE" --arg tenant "$TENANT" \
      --arg ts "$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
      --arg pfile "$(basename "$PROBES")" --arg psha "$probes_sha" --arg rev "$git_rev" \
      '{side: $side, base_url: $base, tenant: $tenant, captured_at: $ts,
        probe_list: {file: $pfile, sha256: $psha}, harness_git_rev: $rev,
        probe_count: length,
        failed_probes: [.[] | select(.result == "fail") | .id],
        probes: .}' "$ENTRIES" > "$OUT/manifest.json"
rm -f "$ENTRIES"

echo "capture: $PROBE_COUNT probes captured to $OUT (manifest.json written)"
if [ "$FAILURES" -gt 0 ]; then
  echo "capture: FAILED — $FAILURES probe(s) mismatched their pinned status; see messages above and failed_probes in $OUT/manifest.json" >&2
  exit 1
fi
echo "capture: OK — every pinned expectation held"
