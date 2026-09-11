#!/usr/bin/env bash
set -euo pipefail

expired_token="${EXPIRED_TOKEN:-}"
origin_manifest_url="${ORIGIN_MANIFEST_URL:-}"
validation_url="${PLAYBACK_VALIDATION_URL:-}"
wrong_resource="${WRONG_RESOURCE:-media/other-content/segment.ts}"
require_extended_cases="${REQUIRE_EXTENDED_CASES:-false}"
result_path="${CDN_RESULT_PATH:-}"
result_tmp="$(mktemp)"
started_at="$(date +%s)"
started_at_iso="$(date -u +%Y-%m-%dT%H:%M:%SZ)"

finalize_results() {
  local exit_code="$?"
  local result_status="PASS"
  [[ "$exit_code" == "0" ]] || result_status="FAIL"
  local completed_at="$(date +%s)"
  local completed_at_iso="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  if [[ -n "$result_path" ]]; then
    mkdir -p "$(dirname "$result_path")"
    jq -s --arg status "$result_status" --arg environment "${CERTIFICATION_ENVIRONMENT:-cdn}" \
      --arg startedAt "$started_at_iso" --arg completedAt "$completed_at_iso" \
      --argjson durationSeconds "$((completed_at - started_at))" \
      '{status:$status,startedAt:$startedAt,completedAt:$completedAt,durationSeconds:$durationSeconds,environment:$environment,testCount:length,passed:(map(select(.status == "PASS")) | length),failed:(map(select(.status == "FAIL")) | length),skipped:0,artifacts:[],failureReason:(if $status == "PASS" then null else "One or more CDN authorization cases failed" end),cases:.}' "$result_tmp" > "$result_path"
  fi
  rm -f "$result_tmp"
  return "$exit_code"
}
trap finalize_results EXIT

missing_required=0
for required_name in CDN_MANIFEST_URL CDN_SEGMENT_URL VALID_TOKEN; do
  if [[ -z "${!required_name:-}" ]]; then
    echo "$required_name is required" >&2
    missing_required=1
  fi
done
if [[ "$missing_required" == "1" ]]; then
  exit 2
fi

with_token() {
  local url="$1" token="$2" separator='?'
  [[ "$url" == *\?* ]] && separator='&'
  printf '%s%stoken=%s' "$url" "$separator" "$token"
}

status() {
  curl --silent --show-error -o /dev/null -w '%{http_code}' "$1"
}

assert_status() {
  local label="$1" actual="$2" expected="$3"
  if [[ ",$expected," != *",$actual,"* ]]; then
    jq -nc --arg label "$label" --arg actual "$actual" --arg expected "$expected" \
      '{label:$label,status:"FAIL",actual:$actual,expected:$expected}' >> "$result_tmp"
    echo "FAIL $label expected=[$expected] actual=$actual" >&2
    exit 1
  fi
  jq -nc --arg label "$label" --arg actual "$actual" --arg expected "$expected" \
    '{label:$label,status:"PASS",actual:$actual,expected:$expected}' >> "$result_tmp"
  echo "PASS $label status=$actual"
}

if [[ "$require_extended_cases" == "true" ]]; then
  missing_extended=0
  for required_name in EXPIRED_TOKEN ORIGIN_MANIFEST_URL PLAYBACK_VALIDATION_URL; do
    if [[ -z "${!required_name:-}" ]]; then
      echo "$required_name is required in strict certification mode" >&2
      missing_extended=1
    fi
  done
  if [[ "$missing_extended" == "1" ]]; then
    exit 2
  fi
fi

assert_status "valid manifest" "$(status "$(with_token "$CDN_MANIFEST_URL" "$VALID_TOKEN")")" "200,206"
assert_status "valid segment" "$(status "$(with_token "$CDN_SEGMENT_URL" "$VALID_TOKEN")")" "200,206"
assert_status "missing grant" "$(status "$CDN_SEGMENT_URL")" "401,403"
assert_status "invalid signature" "$(status "$(with_token "$CDN_SEGMENT_URL" "invalid-token")")" "401,403"

if [[ -n "$expired_token" ]]; then
  assert_status "expired grant" "$(status "$(with_token "$CDN_SEGMENT_URL" "$expired_token")")" "401,403"
fi
if [[ -n "$origin_manifest_url" ]]; then
  assert_status "direct origin" "$(status "$origin_manifest_url")" "401,403"
fi
if [[ -n "$validation_url" ]]; then
  separator='?'
  [[ "$validation_url" == *\?* ]] && separator='&'
  wrong_status="$(status "${validation_url}${separator}token=${VALID_TOKEN}&resource=${wrong_resource}")"
  assert_status "wrong resource" "$wrong_status" "401,403"
fi
