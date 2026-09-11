# Local development

Required tools:

- JDK 17+
- Gradle 8.13+
- Docker with Compose
- PostgreSQL client tooling is optional

Start PostgreSQL and the local S3-compatible object store:

```bash
docker compose -f infrastructure/docker/docker-compose.yml up -d postgres minio

# Create the bucket once with MinIO Client or the console at http://localhost:9001.
mc alias set local http://localhost:9000 minioadmin minioadmin
mc mb --ignore-existing local/media

# Start the API and the FFmpeg media worker after the bucket exists.
docker compose -f infrastructure/docker/docker-compose.yml up -d media-worker
```

Required gateway variables:

```bash
export DATABASE_URL='jdbc:postgresql://localhost:5432/streaming'
export DATABASE_USER='streaming'
export DATABASE_PASSWORD='change-me-locally'
export JWT_SECRET='replace-with-at-least-32-random-characters'
export JWT_ISSUER='streaming-platform'
export JWT_AUDIENCE='streaming-client'
export CORS_ORIGINS='http://localhost:3000'
export CDN_BASE_URL='http://localhost:9000/media'
export PUBLIC_ASSET_BASE_URL='http://localhost:9000/assets'
export MEDIA_STORAGE_ENDPOINT='http://localhost:9000'
export MEDIA_STORAGE_ACCESS_KEY='minioadmin'
export MEDIA_STORAGE_SECRET_KEY='minioadmin'
export MEDIA_STORAGE_BUCKET='media'
# Optional production-like search routing. Omit to use PostgreSQL full-text search locally.
export OPENSEARCH_ENDPOINT='https://opensearch.example.internal'
export OPENSEARCH_INDEX='content'
export OPENSEARCH_API_KEY='do-not-commit-this'
```

Run:

```bash
gradle :backend:api-gateway:run
```

Do not commit a `.env` file or production secrets. Kubernetes deployments must inject credentials through a secret manager or Kubernetes Secret populated by an external secret controller.
