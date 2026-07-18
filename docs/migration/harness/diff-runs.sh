#!/bin/bash
# Compare two capture runs by their manifests (review F-11 remediation).
#
# For every probe id in either manifest, reports one verdict:
#   byte-equal    same status, sha256-identical normalized bodies
#   sorted-equal  same status, bodies identical after sorting every JSON array
#                 (covers deviation D-4, undefined legacy listing order)
#   allowed       diverged, but the probe is listed in the deviation allowlist
#   DIVERGED      anything else (status mismatch, body mismatch, probe missing)
#
# Usage: diff-runs.sh RUN_DIR_A RUN_DIR_B [ALLOWLIST]
#        RUN_DIRs are capture.sh output dirs containing manifest.json.
#        ALLOWLIST defaults to deviation-allowlist.tsv next to this script;
#        rows must cite registered deviations D-1..D-19 (wire-compat-deviations.md).
# Exit:  0 no non-allowlisted divergence; 1 at least one DIVERGED probe;
#        2 usage / missing manifest.
set -euo pipefail

A=${1:-}
B=${2:-}
if [ -z "$A" ] || [ -z "$B" ]; then
  echo "usage: diff-runs.sh RUN_DIR_A RUN_DIR_B [allowlist.tsv]" >&2
  exit 2
fi
SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
ALLOWLIST=${3:-$SCRIPT_DIR/deviation-allowlist.tsv}

for d in "$A" "$B"; do
  [ -r "$d/manifest.json" ] || { echo "diff-runs: no readable manifest.json in $d" >&2; exit 2; }
done

mf() { # mf RUN_DIR PROBE_ID FIELD — one field of one probe entry ('' if absent)
  jq -r --arg id "$2" --arg f "$3" \
    '[.probes[] | select(.id == $id)] | if length == 0 then "" else .[0][$f] // "" end' \
    "$1/manifest.json"
}

allowed() { # allowed PROBE_ID — prints deviation ids; rc 0 iff allowlisted
  [ -r "$ALLOWLIST" ] || return 1
  awk -F'\t' -v id="$1" '!/^[[:space:]]*(#|$)/ && $1 == id { print $2; found = 1 } END { exit !found }' \
    "$ALLOWLIST"
}

sort_arrays() { jq -S 'walk(if type == "array" then sort else . end)' "$1" 2>/dev/null || true; }

echo "diff-runs: A=$A  B=$B"
echo "diff-runs: allowlist=$ALLOWLIST"
echo

ids=$( (jq -r '.probes[].id' "$A/manifest.json"; jq -r '.probes[].id' "$B/manifest.json") | sort -u )

DIVERGED=0 BYTE=0 SORTED=0 ALLOWED=0

for id in $ids; do
  file_a=$(mf "$A" "$id" response_file)
  file_b=$(mf "$B" "$id" response_file)
  verdict="" detail=""

  if [ -z "$file_a" ] || [ -z "$file_b" ]; then
    verdict=DIVERGED
    if [ -z "$file_a" ]; then detail="probe missing from $A/manifest.json"; else detail="probe missing from $B/manifest.json"; fi
  else
    status_a=$(mf "$A" "$id" actual_status)
    status_b=$(mf "$B" "$id" actual_status)
    sha_a=$(mf "$A" "$id" body_sha256)
    sha_b=$(mf "$B" "$id" body_sha256)
    if [ "$status_a" != "$status_b" ]; then
      verdict=DIVERGED detail="status $status_a vs $status_b"
    elif [ "$sha_a" = "$sha_b" ]; then
      verdict=byte-equal
    else
      na=$(sort_arrays "$A/$file_a")
      nb=$(sort_arrays "$B/$file_b")
      if [ -n "$na" ] && [ "$na" = "$nb" ]; then
        verdict=sorted-equal detail="array order only (D-4)"
      else
        verdict=DIVERGED detail="body differs (status $status_a on both); diff $A/$file_a $B/$file_b"
      fi
    fi
  fi

  if [ "$verdict" = DIVERGED ]; then
    if dev=$(allowed "$id"); then
      verdict=allowed detail="registered deviation $dev — $detail"
      ALLOWED=$((ALLOWED+1))
    else
      DIVERGED=$((DIVERGED+1))
    fi
  elif [ "$verdict" = byte-equal ]; then BYTE=$((BYTE+1))
  else SORTED=$((SORTED+1))
  fi

  printf '  %-28s %-12s %s\n' "$id" "$verdict" "$detail"
done

echo
echo "diff-runs: byte-equal=$BYTE sorted-equal=$SORTED allowed=$ALLOWED diverged=$DIVERGED"
if [ "$DIVERGED" -gt 0 ]; then
  echo "diff-runs: FAILED — $DIVERGED probe(s) diverged without a registered-deviation allowlist entry" >&2
  exit 1
fi
echo "diff-runs: OK — no unregistered divergence"
