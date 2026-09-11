#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "$script_dir/../.." && pwd)"
compose_file="${COMPOSE_FILE:-$repo_root/infrastructure/docker/docker-compose.yml}"
certification_dir="${CERTIFICATION_DIR:-$repo_root/certification}"
logs_dir="$certification_dir/logs"
api_url="${API_URL:-http://localhost:8080}"
certification_mode="${CERTIFICATION_MODE:-pending}"
fail_on_pending="${CERTIFICATION_FAIL_ON_PENDING:-false}"
[[ "$certification_mode" == "strict" ]] && fail_on_pending=true
certification_environment="${CERTIFICATION_ENVIRONMENT:-local}"
run_repository_tests="${RUN_REPOSITORY_TESTS:-true}"
stop_compose="${STOP_COMPOSE:-false}"

mkdir -p "$logs_dir"
umask 077

write_gate_artifact() {
  local artifact="$1" gate="$2" status="$3" reason="$4" evidence="$5"
  local started_at="${6:-$(date -u +%Y-%m-%dT%H:%M:%SZ)}"
  local completed_at="${7:-$started_at}"
  local duration_seconds="${8:-0}"
  local artifact_path="$artifact"
  [[ "$artifact_path" = /* ]] || artifact_path="$certification_dir/$artifact_path"
  jq -n \
    --arg gate "$gate" \
    --arg status "$status" \
    --arg reason "$reason" \
    --arg evidence "$evidence" \
    --arg environment "$certification_environment" \
    --arg startedAt "$started_at" \
    --arg completedAt "$completed_at" \
    --argjson durationSeconds "$duration_seconds" \
    '{gate:$gate,status:$status,hardGate:true,startedAt:$startedAt,completedAt:$completedAt,durationSeconds:$durationSeconds,environment:$environment,testCount:(if $status == "PASS" or $status == "FAIL" then 1 else 0 end),passed:(if $status == "PASS" then 1 else 0 end),failed:(if $status == "FAIL" then 1 else 0 end),skipped:(if $status == "NOT_EXECUTED" then 1 else 0 end),artifacts:[$evidence],failureReason:(if $status == "PASS" then null else $reason end),reason:$reason,evidence:$evidence}' \
    > "$artifact_path"
}

run_command() {
  local log_file="$1"
  shift
  set +e
  "$@" > "$log_file" 2>&1
  local exit_code="$?"
  set -e
  return "$exit_code"
}

run_shell_command() {
  local log_file="$1"
  local command_text="$2"
  set +e
  bash -lc "$command_text" > "$log_file" 2>&1
  local exit_code="$?"
  set -e
  return "$exit_code"
}

run_repository_gate() {
  local artifact="repository.json"
  local log_file="$logs_dir/repository.log"
  local started_epoch="$(date +%s)"
  local started_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  if [[ "$run_repository_tests" != "true" ]]; then
    write_gate_artifact "$artifact" repository NOT_EXECUTED "Repository tests were disabled by RUN_REPOSITORY_TESTS" "logs/repository.log" "$started_at" "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$(( $(date +%s) - started_epoch ))"
    return 0
  fi
  if run_command "$log_file" "$repo_root/gradlew" test --no-daemon; then
    write_gate_artifact "$artifact" repository PASS "Gradle repository tests passed" "logs/repository.log" "$started_at" "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$(( $(date +%s) - started_epoch ))"
  else
    write_gate_artifact "$artifact" repository FAIL "Gradle repository tests failed" "logs/repository.log" "$started_at" "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$(( $(date +%s) - started_epoch ))"
  fi
}

run_docker_gate() {
  local log_file="$logs_dir/docker-smoke.log"
  local started_epoch="$(date +%s)"
  local started_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  if ! command -v docker >/dev/null 2>&1 || ! docker compose version >/dev/null 2>&1; then
    write_gate_artifact docker.json docker NOT_EXECUTED "Docker Compose is unavailable in this environment" "logs/docker-smoke.log" "$started_at" "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$(( $(date +%s) - started_epoch ))"
    echo "Docker certification: NOT_EXECUTED (Docker Compose unavailable)"
    return 0
  fi

  if run_command "$log_file" env COMPOSE_FILE="$compose_file" API_URL="$api_url" "$script_dir/run-infrastructure-smoke.sh"; then
    docker compose -f "$compose_file" ps >> "$log_file" 2>&1 || true
    write_gate_artifact docker.json docker PASS "Docker health and readiness smoke passed" "logs/docker-smoke.log" "$started_at" "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$(( $(date +%s) - started_epoch ))"
    echo "Docker certification: PASS"
  else
    local exit_code="$?"
    if [[ "$exit_code" == "2" ]]; then
      write_gate_artifact docker.json docker NOT_EXECUTED "Docker smoke was not executed" "logs/docker-smoke.log" "$started_at" "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$(( $(date +%s) - started_epoch ))"
    else
      write_gate_artifact docker.json docker FAIL "Docker health or readiness smoke failed" "logs/docker-smoke.log" "$started_at" "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$(( $(date +%s) - started_epoch ))"
    fi
    echo "Docker certification: $(jq -r '.status' "$certification_dir/docker.json")"
  fi
}

run_golden_gate() {
  local log_file="$logs_dir/golden-path.log"
  local result_file="$certification_dir/golden-path.json"
  if [[ "$(jq -r '.status' "$certification_dir/docker.json")" != "PASS" ]]; then
    write_gate_artifact "$result_file" golden_path NOT_EXECUTED "Golden path requires a passing Docker gate" "logs/golden-path.log"
    return 0
  fi
  if [[ -z "${ADMIN_TOKEN:-}" || -z "${CONTENT_ID:-}" ]]; then
    write_gate_artifact "$result_file" golden_path NOT_EXECUTED "ADMIN_TOKEN and CONTENT_ID are required for the seeded golden path" "logs/golden-path.log"
    return 0
  fi

  set +e
  docker compose -f "$compose_file" --profile e2e run --rm e2e > "$log_file" 2>&1
  local exit_code="$?"
  set -e
  if [[ "$exit_code" == "0" && -f "$result_file" && "$(jq -r '.status // "FAIL"' "$result_file")" == "PASS" ]]; then
    echo "Golden path certification: PASS"
    return 0
  fi
  if [[ ! -f "$result_file" ]]; then
    write_gate_artifact "$result_file" golden_path FAIL "Golden path runner failed before producing a trace" "logs/golden-path.log"
  fi
  echo "Golden path certification: FAIL"
}

run_cdn_gate() {
  local result_file="$certification_dir/cdn-results.json"
  local log_file="$logs_dir/cdn.log"
  if [[ -z "${CDN_MANIFEST_URL:-}" || -z "${CDN_SEGMENT_URL:-}" || -z "${VALID_TOKEN:-}" ]]; then
    write_gate_artifact "$result_file" cdn NOT_EXECUTED "CDN_MANIFEST_URL, CDN_SEGMENT_URL, and VALID_TOKEN are required" "logs/cdn.log"
    return 0
  fi
  set +e
  env CDN_RESULT_PATH="$result_file" REQUIRE_EXTENDED_CASES=true "$script_dir/cdn-negative-cases.sh" > "$log_file" 2>&1
  local exit_code="$?"
  set -e
  if [[ "$exit_code" != "0" && ! -f "$result_file" ]]; then
    write_gate_artifact "$result_file" cdn FAIL "CDN negative-case runner failed before producing results" "logs/cdn.log"
  fi
  if [[ "$exit_code" == "0" ]]; then
    echo "CDN certification: PASS"
  else
    echo "CDN certification: FAIL"
  fi
}

run_hook_gate() {
  local gate="$1" artifact="$2" command_var="$3" result_var="$4"
  local command_text="${!command_var:-}"
  local result_source="${!result_var:-}"
  local log_file="$logs_dir/$gate.log"
  local started_epoch="$(date +%s)"
  local started_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  if [[ -z "$command_text" && -z "$result_source" ]]; then
    write_gate_artifact "$artifact" "$gate" NOT_EXECUTED "No certification command or evidence artifact was supplied" "logs/$gate.log" "$started_at" "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$(( $(date +%s) - started_epoch ))"
    return 0
  fi
  if [[ -n "$result_source" ]]; then
    if [[ ! -f "$result_source" ]] || ! jq empty "$result_source" >/dev/null 2>&1; then
      write_gate_artifact "$artifact" "$gate" FAIL "Supplied evidence artifact is missing or invalid JSON" "logs/$gate.log"
      return 0
    fi
    cp "$result_source" "$certification_dir/$artifact"
    return 0
  fi
  if run_shell_command "$log_file" "$command_text"; then
    write_gate_artifact "$artifact" "$gate" PASS "Certification command passed" "logs/$gate.log" "$started_at" "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$(( $(date +%s) - started_epoch ))"
  else
    write_gate_artifact "$artifact" "$gate" FAIL "Certification command failed" "logs/$gate.log" "$started_at" "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$(( $(date +%s) - started_epoch ))"
  fi
}

run_repository_gate
run_docker_gate
run_golden_gate
run_cdn_gate
run_hook_gate worker_recovery worker-metrics.json WORKER_RECOVERY_COMMAND WORKER_METRICS_RESULT_PATH
run_hook_gate drm drm-results.json DRM_CERTIFICATION_COMMAND DRM_RESULTS_PATH
run_hook_gate native_playback playback-results.json PLAYBACK_CERTIFICATION_COMMAND PLAYBACK_RESULTS_PATH
run_hook_gate offline offline-results.json OFFLINE_CERTIFICATION_COMMAND OFFLINE_RESULTS_PATH
run_hook_gate load load-results.json LOAD_TEST_COMMAND LOAD_TEST_RESULTS_PATH
run_hook_gate security security-results.json SECURITY_TEST_COMMAND SECURITY_RESULTS_PATH

if [[ "$stop_compose" == "true" ]] && command -v docker >/dev/null 2>&1; then
  docker compose -f "$compose_file" down >> "$logs_dir/docker-smoke.log" 2>&1 || true
fi

CERTIFICATION_DIR="$certification_dir" "$script_dir/build-certification-report.sh"
overall_status="$(jq -r '.overallStatus' "$certification_dir/certification.json")"
has_failure="$(jq -r 'any(.gates[]; .status == "FAIL")' "$certification_dir/certification.json")"
if [[ "$overall_status" == "NO-GO" && ("$fail_on_pending" == "true" || "$has_failure" == "true") ]]; then
  exit 1
fi
