FROM ubuntu:24.04
RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates curl ffmpeg jq \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /workspace
ENTRYPOINT ["/workspace/infrastructure/e2e/golden-path.sh"]
