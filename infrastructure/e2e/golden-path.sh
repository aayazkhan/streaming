#!/usr/bin/env bash
set -euo pipefail

: "${ADMIN_TOKEN:?ADMIN_TOKEN must be set to an administrator access token}"
: "${CONTENT_ID:?CONTENT_ID must reference an existing seeded content row}"

api_base_url="${API_BASE_URL:-http://api-gateway:8080/v1}"
fixture_path="${FIXTURE_PATH:-infrastructure/e2e/fixtures/golden.mp4}"
part_size_bytes="${PART_SIZE_BYTES:-5242880}"
poll_attempts="${POLL_ATTEMPTS:-60}"
poll_delay_seconds="${POLL_DELAY_SECONDS:-2}"
generate_fixture="${GENERATE_FIXTURE:-false}"
result_path="${E2E_RESULT_PATH:-/tmp/streaming-golden-result.json}"
max_processing_seconds="${MAX_PROCESSING_SECONDS:-}"
correlation_id="${E2E_CORRELATION_ID:-golden-$(date +%s)}"
started_at="$(date +%s)"
trace_written=false
last_state="NOT_STARTED"
last_job_response='null'
upload_id=""
headers_file=""
parts_dir=""
parts_file=""

write_failure_trace() {
  local exit_code="$1"
  if [[ "$trace_written" == "true" || "$exit_code" == "0" ]]; then
    return "$exit_code"
  fi
  mkdir -p "$(dirname "$result_path")"
  jq -n \
    --arg correlationId "$correlation_id" \
    --arg state "$last_state" \
    --arg uploadId "$upload_id" \
    --arg environment "${CERTIFICATION_ENVIRONMENT:-docker-e2e}" \
    --argjson job "$last_job_response" \
    --argjson startedAt "$started_at" \
    --argjson failedAt "$(date +%s)" \
    --argjson exitCode "$exit_code" \
    '{status:"FAIL",correlationId:$correlationId,startedAt:$startedAt,failedAt:$failedAt,exitCode:$exitCode,environment:$environment,testCount:1,passed:0,failed:1,skipped:0,artifacts:[],failureReason:"Golden path did not complete successfully",uploadId:($uploadId // null),state:$state,job:$job}' \
    > "$result_path"
  return "$exit_code"
}

cleanup_and_trace() {
  local exit_code="$?"
  write_failure_trace "$exit_code" || true
  [[ -z "$headers_file" ]] || rm -f "$headers_file"
  [[ -z "$parts_file" ]] || rm -f "$parts_file"
  [[ -z "$parts_dir" ]] || rm -rf "$parts_dir"
  return "$exit_code"
}
trap cleanup_and_trace EXIT

for command_name in curl jq openssl base64; do
  command -v "$command_name" >/dev/null 2>&1 || { echo "$command_name is required" >&2; exit 2; }
done
if [[ ! -f "$fixture_path" && "$generate_fixture" == "true" ]]; then
  command -v ffmpeg >/dev/null 2>&1 || { echo "ffmpeg is required to generate the golden fixture" >&2; exit 2; }
  mkdir -p "$(dirname "$fixture_path")"
  ffmpeg -hide_banner -loglevel error -y \
    -f lavfi -i "testsrc=size=640x360:rate=30" \
    -f lavfi -i "sine=frequency=1000:sample_rate=48000" \
    -t 10 -c:v libx264 -pix_fmt yuv420p -c:a aac -movflags +faststart "$fixture_path"
fi
[[ -f "$fixture_path" ]] || { echo "Fixture does not exist: $fixture_path" >&2; exit 2; }

health_url="${api_base_url%/v1}/health"
for attempt in $(seq 1 30); do
  if curl --fail --silent --show-error "$health_url" >/dev/null; then break; fi
  if [[ "$attempt" == 30 ]]; then echo "API gateway did not become healthy" >&2; exit 1; fi
  sleep 2
done

file_name="$(basename "$fixture_path")"
size_bytes="$(wc -c < "$fixture_path" | tr -d '[:space:]')"
if command -v shasum >/dev/null 2>&1; then
  checksum="$(shasum -a 256 "$fixture_path" | awk '{print $1}')"
else
  checksum="$(sha256sum "$fixture_path" | awk '{print $1}')"
fi

auth_header="Authorization: Bearer ${ADMIN_TOKEN}"
upload_response="$(curl --fail --silent --show-error \
  -H "$auth_header" -H "X-Correlation-Id: $correlation_id" -H 'Content-Type: application/json' \
  -X POST "$api_base_url/media/uploads/multipart" \
  --data "{\"base\":{\"contentId\":\"$CONTENT_ID\",\"fileName\":\"$file_name\",\"contentType\":\"video/mp4\",\"sizeBytes\":$size_bytes,\"checksumSha256\":\"$checksum\"},\"partSizeBytes\":$part_size_bytes}")"
upload_id="$(jq -er '.upload.id' <<<"$upload_response")"

headers_file="$(mktemp)"
parts_dir="$(mktemp -d)"
parts_file="$(mktemp)"
split -b "$part_size_bytes" -d -a 6 "$fixture_path" "$parts_dir/part-"
part_number=1
for part_path in "$parts_dir"/part-*; do
  part_checksum="$(openssl dgst -sha256 -binary "$part_path" | base64 | tr -d '\n')"
  part_response="$(curl --fail --silent --show-error \
    -H "$auth_header" -H "X-Correlation-Id: $correlation_id" -H "X-Upload-Part-Checksum-Sha256: $part_checksum" \
    -X POST "$api_base_url/media/uploads/$upload_id/parts/$part_number")"
  part_url="$(jq -er '.grant.url' <<<"$part_response")"
  : > "$headers_file"
  curl --fail --silent --show-error -D "$headers_file" -o /dev/null \
    -H "x-amz-checksum-sha256: $part_checksum" -X PUT "$part_url" --data-binary "@$part_path"
  etag="$(awk 'BEGIN { IGNORECASE = 1 } /^etag:/ { sub(/^etag:[[:space:]]*/, ""); print }' "$headers_file" | tail -n 1 | tr -d '\r')"
  [[ -n "$etag" ]] || { echo "Multipart upload did not return an ETag for part $part_number" >&2; exit 1; }
  jq -cn --argjson partNumber "$part_number" --arg etag "$etag" --arg checksum "$part_checksum" \
    '{partNumber:$partNumber,etag:$etag,checksumSha256:$checksum}' >> "$parts_file"
  part_number=$((part_number + 1))
done
part_count=$((part_number - 1))

complete_response="$(curl --fail --silent --show-error \
  -H "$auth_header" -H "X-Correlation-Id: $correlation_id" -H 'Content-Type: application/json' \
  -X POST "$api_base_url/media/uploads/$upload_id/complete-multipart" \
  --data "$(jq -s -c '{parts:.}' "$parts_file")")"
processing_queued="$(jq -r '.processingQueued' <<<"$complete_response")"
[[ "$processing_queued" == "true" ]] || { echo "Media job was not queued" >&2; exit 1; }

echo "Uploaded fixture; polling media job $upload_id"
for ((attempt = 1; attempt <= poll_attempts; attempt++)); do
  job_response="$(curl --fail --silent --show-error -H "$auth_header" -H "X-Correlation-Id: $correlation_id" "$api_base_url/media/jobs/$upload_id")"
  last_job_response="$job_response"
  state="$(jq -r '.state' <<<"$job_response")"
  last_state="$state"
  progress="$(jq -r '.progressPercent' <<<"$job_response")"
  echo "attempt=$attempt state=$state progress=$progress"
  case "$state" in
    READY)
      completed_at="$(date +%s)"
      elapsed_seconds=$((completed_at - started_at))
      jq -e '.hlsManifestKey != null and .dashManifestKey != null and .progressPercent == 100' <<<"$job_response" >/dev/null || {
        echo "READY job is missing required HLS/DASH artifacts or 100% progress" >&2
        exit 1
      }
      if [[ -n "$max_processing_seconds" && "$elapsed_seconds" -gt "$max_processing_seconds" ]]; then
        echo "Media processing exceeded threshold: elapsed=${elapsed_seconds}s threshold=${max_processing_seconds}s" >&2
        exit 1
      fi
      mkdir -p "$(dirname "$result_path")"
      jq -n --arg status "PASS" --arg correlationId "$correlation_id" --argjson job "$job_response" \
        --arg environment "${CERTIFICATION_ENVIRONMENT:-docker-e2e}" \
        --argjson startedAt "$started_at" --argjson completedAt "$completed_at" --argjson elapsedSeconds "$elapsed_seconds" \
        --argjson partCount "$part_count" \
        --argjson thresholdSeconds "${max_processing_seconds:-null}" \
        '{status:$status,correlationId:$correlationId,startedAt:$startedAt,completedAt:$completedAt,elapsedSeconds:$elapsedSeconds,partCount:$partCount,thresholdSeconds:$thresholdSeconds,environment:$environment,testCount:1,passed:1,failed:0,skipped:0,artifacts:[$job.hlsManifestKey,$job.dashManifestKey,$job.posterKey,$job.previewKey] | map(select(. != null)),failureReason:null,jobId:$job.id,contentId:$job.contentId,state:$job.state,workerId:$job.workerId,attempts:$job.attempts,retryCount:(($job.attempts // 0) - 1),heartbeatAt:$job.heartbeatAt,progressPercent:$job.progressPercent,hlsManifestKey:$job.hlsManifestKey,dashManifestKey:$job.dashManifestKey,posterKey:$job.posterKey,previewKey:$job.previewKey}' | tee "$result_path"
      trace_written=true
      echo "Certification trace: $result_path"
      exit 0
      ;;
    FAILED|CANCELLED)
      jq '{id,state,failureCode,attempts}' <<<"$job_response" >&2
      exit 1
      ;;
  esac
  sleep "$poll_delay_seconds"
done

echo "Media job did not reach READY within the polling window" >&2
exit 1
