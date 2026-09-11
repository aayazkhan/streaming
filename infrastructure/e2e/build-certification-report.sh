#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "$script_dir/../.." && pwd)"
certification_dir="${CERTIFICATION_DIR:-$repo_root/certification}"
certification_phase="${CERTIFICATION_PHASE:-12}"
generated_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
records_file="$(mktemp)"
trap 'rm -f "$records_file"' EXIT

mkdir -p "$certification_dir"

gate_specs=(
  "repository|repository.json"
  "docker|docker.json"
  "golden_path|golden-path.json"
  "worker_recovery|worker-metrics.json"
  "cdn|cdn-results.json"
  "drm|drm-results.json"
  "native_playback|playback-results.json"
  "offline|offline-results.json"
  "load|load-results.json"
  "security|security-results.json"
)

for spec in "${gate_specs[@]}"; do
  gate="${spec%%|*}"
  artifact="${spec#*|}"
  artifact_path="$certification_dir/$artifact"
  if [[ ! -f "$artifact_path" ]]; then
    jq -nc --arg gate "$gate" --arg artifact "$artifact" --arg generatedAt "$generated_at" \
      '{gate:$gate,status:"NOT_EXECUTED",hardGate:true,artifact:$artifact,startedAt:$generatedAt,completedAt:$generatedAt,durationSeconds:0,environment:"unspecified",testCount:0,passed:0,failed:0,skipped:1,artifacts:[],failureReason:"Evidence artifact has not been produced",reason:"Evidence artifact has not been produced",evidence:null}' >> "$records_file"
    continue
  fi

  if ! jq empty "$artifact_path" >/dev/null 2>&1; then
    jq -nc --arg gate "$gate" --arg artifact "$artifact" --arg generatedAt "$generated_at" \
      '{gate:$gate,status:"FAIL",hardGate:true,artifact:$artifact,startedAt:$generatedAt,completedAt:$generatedAt,durationSeconds:0,environment:"unspecified",testCount:1,passed:0,failed:1,skipped:0,artifacts:[],failureReason:"Evidence artifact is not valid JSON",reason:"Evidence artifact is not valid JSON",evidence:null}' >> "$records_file"
    continue
  fi

  status="$(jq -r '.status // "PENDING"' "$artifact_path")"
  case "$status" in
    PASS|FAIL|NOT_EXECUTED) normalized_status="$status" ;;
    PENDING) normalized_status="NOT_EXECUTED" ;;
    *)
      jq -nc --arg gate "$gate" --arg artifact "$artifact" --arg status "$status" --arg generatedAt "$generated_at" \
        '{gate:$gate,status:"FAIL",hardGate:true,artifact:$artifact,startedAt:$generatedAt,completedAt:$generatedAt,durationSeconds:0,environment:"unspecified",testCount:1,passed:0,failed:1,skipped:0,artifacts:[],failureReason:("Unsupported evidence status: " + $status),reason:("Unsupported evidence status: " + $status),evidence:null}' >> "$records_file"
      continue
      ;;
  esac

  jq -c --arg gate "$gate" --arg artifact "$artifact" --arg status "$normalized_status" --arg generatedAt "$generated_at" \
    '{gate:$gate,status:$status,hardGate:true,artifact:$artifact,startedAt:(.startedAt // $generatedAt),completedAt:(.completedAt // .startedAt // $generatedAt),durationSeconds:(.durationSeconds // .elapsedSeconds // 0),environment:(.environment // "unspecified"),testCount:(.testCount // 0),passed:(.passed // 0),failed:(.failed // 0),skipped:(.skipped // (if $status == "NOT_EXECUTED" then 1 else 0 end)),artifacts:(.artifacts // []),failureReason:(.failureReason // (if $status == "PASS" then null else (.reason // "No failure reason supplied") end)),reason:(.reason // "Evidence artifact recorded"),evidence:(.evidence // null)}' \
    "$artifact_path" >> "$records_file"
done

gates_json="$(jq -s . "$records_file")"
overall_status="$(jq -r 'if all(.[]; .status == "PASS") then "GO" else "NO-GO" end' <<<"$gates_json")"
execution_status="$(jq -r 'if any(.[]; .status == "NOT_EXECUTED") then "INCOMPLETE" else "COMPLETE" end' <<<"$gates_json")"
platform_status="CERTIFICATION_PENDING"
if [[ "$overall_status" == "GO" ]]; then
  platform_status="PRODUCTION_CANDIDATE"
elif [[ "$execution_status" == "COMPLETE" ]]; then
  platform_status="NO_GO"
fi

jq -n \
  --argjson phase "$certification_phase" \
  --arg generatedAt "$generated_at" \
  --arg overallStatus "$overall_status" \
  --arg executionStatus "$execution_status" \
  --arg platformStatus "$platform_status" \
  --argjson gates "$gates_json" \
  '{phase:$phase,generatedAt:$generatedAt,overallStatus:$overallStatus,executionStatus:$executionStatus,platformStatus:$platformStatus,productionReady:($overallStatus == "GO"),gates:$gates}' \
  > "$certification_dir/certification.json"
cp "$certification_dir/certification.json" "$certification_dir/summary.json"

{
  echo "# Phase $certification_phase production certification report"
  echo
  echo "Generated: $generated_at"
  echo
  echo "Overall decision: **$overall_status**"
  echo
  echo "Execution status: **$execution_status**"
  echo
  echo "Production readiness: **$([[ "$overall_status" == "GO" ]] && echo PASS || echo NOT CERTIFIED)**"
  echo
  echo "| Gate | Status | Duration | Passed | Failed | Skipped | Evidence | Failure reason |"
  echo "|---|---|---:|---:|---:|---:|---|---|"
  jq -r '.[] | "| `\(.gate)` | **\(.status)** | \(.durationSeconds)s | \(.passed) | \(.failed) | \(.skipped) | `\(.artifact)` | \(.failureReason // "") |"' <<<"$gates_json"
  echo
  echo "A GO requires every hard gate to have a PASS evidence artifact. Any FAIL or NOT_EXECUTED gate is NO-GO."
} > "$certification_dir/certification.md"
cp "$certification_dir/certification.md" "$certification_dir/certification-report.md"

echo "Certification JSON: $certification_dir/certification.json"
echo "Certification Markdown: $certification_dir/certification.md"
echo "Overall decision: $overall_status"
