resource "kubernetes_deployment_v1" "api_gateway" {
  metadata {
    name   = "streaming-api-gateway"
    labels = { "app.kubernetes.io/name" = "streaming-api-gateway" }
  }
  spec {
    replicas = 1
    selector {
      match_labels = { "app.kubernetes.io/name" = "streaming-api-gateway" }
    }
    template {
      metadata {
        labels = { "app.kubernetes.io/name" = "streaming-api-gateway" }
        # Same image tag every build ("streaming/api-gateway:local"), so Kubernetes has no way to
        # detect a new image was loaded — this annotation ties the pod template to the image build's
        # identity so a rebuilt image triggers a real rollout instead of silently keeping stale pods.
        annotations = { "streaming.platform/image-build" = null_resource.build_and_load_images.id }
      }
      spec {
        container {
          name              = "api-gateway"
          image             = "streaming/api-gateway:local"
          image_pull_policy = "Never"
          port {
            name           = "http"
            container_port = 8080
          }
          env {
            name  = "PORT"
            value = "8080"
          }
          env {
            name = "DATABASE_URL"
            value_from {
              secret_key_ref {
                name = kubernetes_secret.streaming_database.metadata[0].name
                key  = "jdbc-url"
              }
            }
          }
          env {
            name = "DATABASE_USER"
            value_from {
              secret_key_ref {
                name = kubernetes_secret.streaming_database.metadata[0].name
                key  = "username"
              }
            }
          }
          env {
            name = "DATABASE_PASSWORD"
            value_from {
              secret_key_ref {
                name = kubernetes_secret.streaming_database.metadata[0].name
                key  = "password"
              }
            }
          }
          env {
            name = "JWT_SECRET"
            value_from {
              secret_key_ref {
                name = kubernetes_secret.streaming_identity.metadata[0].name
                key  = "jwt-secret"
              }
            }
          }
          env {
            name = "CORS_ORIGINS"
            value_from {
              config_map_key_ref {
                name = kubernetes_config_map.streaming_api_config.metadata[0].name
                key  = "cors-origins"
              }
            }
          }
          env {
            name = "CDN_BASE_URL"
            value_from {
              config_map_key_ref {
                name = kubernetes_config_map.streaming_api_config.metadata[0].name
                key  = "cdn-base-url"
              }
            }
          }
          env {
            name = "PUBLIC_ASSET_BASE_URL"
            value_from {
              config_map_key_ref {
                name = kubernetes_config_map.streaming_api_config.metadata[0].name
                key  = "public-asset-base-url"
              }
            }
          }
          env {
            name = "MEDIA_STORAGE_ENDPOINT"
            value_from {
              config_map_key_ref {
                name = kubernetes_config_map.streaming_api_config.metadata[0].name
                key  = "media-storage-endpoint"
              }
            }
          }
          env {
            name = "MEDIA_STORAGE_PUBLIC_ENDPOINT"
            value_from {
              config_map_key_ref {
                name = kubernetes_config_map.streaming_api_config.metadata[0].name
                key  = "media-storage-public-endpoint"
              }
            }
          }
          env {
            name = "MEDIA_STORAGE_BUCKET"
            value_from {
              config_map_key_ref {
                name = kubernetes_config_map.streaming_api_config.metadata[0].name
                key  = "media-storage-bucket"
              }
            }
          }
          env {
            name = "MEDIA_STORAGE_ACCESS_KEY"
            value_from {
              secret_key_ref {
                name = kubernetes_secret.streaming_media_storage.metadata[0].name
                key  = "access-key"
              }
            }
          }
          env {
            name = "MEDIA_STORAGE_SECRET_KEY"
            value_from {
              secret_key_ref {
                name = kubernetes_secret.streaming_media_storage.metadata[0].name
                key  = "secret-key"
              }
            }
          }
          readiness_probe {
            http_get {
              path = "/ready"
              port = "http"
            }
            initial_delay_seconds = 5
            period_seconds        = 10
          }
          liveness_probe {
            http_get {
              path = "/health"
              port = "http"
            }
            initial_delay_seconds = 10
            period_seconds        = 20
          }
        }
      }
    }
  }
  depends_on = [
    null_resource.build_and_load_images,
    kubernetes_deployment_v1.postgres,
    kubernetes_job_v1.minio_bootstrap,
  ]
}

resource "kubernetes_service_v1" "api_gateway" {
  metadata {
    name = "streaming-api-gateway"
  }
  spec {
    selector = { "app.kubernetes.io/name" = "streaming-api-gateway" }
    port {
      name        = "http"
      port        = 80
      target_port = "http"
    }
  }
  depends_on = [null_resource.kind_cluster]
}
