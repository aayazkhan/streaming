#!/usr/bin/env bash
set -euo pipefail

output_path="${1:-infrastructure/e2e/fixtures/golden.mp4}"
if ! command -v ffmpeg >/dev/null 2>&1; then
  echo "ffmpeg is required to generate the golden media fixture" >&2
  exit 2
fi

mkdir -p "$(dirname "$output_path")"
ffmpeg -hide_banner -loglevel error -y \
  -f lavfi -i "testsrc=size=640x360:rate=30" \
  -f lavfi -i "sine=frequency=1000:sample_rate=48000" \
  -t 10 -c:v libx264 -pix_fmt yuv420p -c:a aac -movflags +faststart "$output_path"

if command -v shasum >/dev/null 2>&1; then
  shasum -a 256 "$output_path"
else
  sha256sum "$output_path"
fi
