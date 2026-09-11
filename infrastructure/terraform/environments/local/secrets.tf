# Generated at apply time, never committed — matches the policy already stated in
# infrastructure/terraform/README.md ("credentials must be passed through a secret manager, never
# committed to .tfvars"). Names/keys match what infrastructure/kubernetes/*.yaml already expects
# via secretKeyRef/configMapKeyRef, so those hand-authored manifests stay valid for a real cluster.

resource "random_password" "jwt_secret" {
  length  = 48
  special = false
}

resource "random_password" "db_password" {
  length  = 24
  special = false
}

resource "random_password" "minio_secret_key" {
  length  = 24
  special = false
}

resource "kubernetes_secret" "streaming_database" {
  metadata {
    name = "streaming-database"
  }
  data = {
    "jdbc-url" = "jdbc:postgresql://streaming-postgres:5432/streaming"
    "username" = "streaming"
    "password" = random_password.db_password.result
  }
  depends_on = [null_resource.kind_cluster]
}

resource "kubernetes_secret" "streaming_identity" {
  metadata {
    name = "streaming-identity"
  }
  data = {
    "jwt-secret" = random_password.jwt_secret.result
  }
  depends_on = [null_resource.kind_cluster]
}

resource "kubernetes_secret" "streaming_media_storage" {
  metadata {
    name = "streaming-media-storage"
  }
  data = {
    "access-key" = "streaming-minio"
    "secret-key" = random_password.minio_secret_key.result
  }
  depends_on = [null_resource.kind_cluster]
}

resource "kubernetes_config_map" "streaming_api_config" {
  metadata {
    name = "streaming-api-config"
  }
  data = {
    "cors-origins"           = "http://localhost:${var.web_host_port}"
    "cdn-base-url"           = "http://localhost:${var.minio_host_port}/media"
    "public-asset-base-url"  = "http://localhost:${var.minio_host_port}/assets"
    "media-storage-endpoint"        = "http://streaming-minio:9000"
    "media-storage-public-endpoint" = "http://localhost:${var.minio_host_port}"
    "media-storage-bucket"          = "media"
  }
  depends_on = [null_resource.kind_cluster]
}
