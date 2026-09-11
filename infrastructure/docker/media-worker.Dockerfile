FROM gradle:8.13-jdk17 AS build
WORKDIR /workspace
COPY . .
RUN gradle :backend:media-worker:installDist --no-daemon

FROM eclipse-temurin:17-jre-jammy
RUN apt-get update \
    && apt-get install -y --no-install-recommends ffmpeg \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /workspace/backend/media-worker/build/install/media-worker/ ./
RUN mkdir -p /tmp/streaming-media-worker && chown -R 10001:10001 /app /tmp/streaming-media-worker
USER 10001
ENTRYPOINT ["./bin/media-worker"]
