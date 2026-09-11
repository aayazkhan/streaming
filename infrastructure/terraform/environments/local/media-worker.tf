resource "kubernetes_deployment_v1" "media_worker" {
  metadata {
    name   = "streaming-media-worker"
    labels = { "app.kubernetes.io/name" = "streaming-media-worker" }
  }
  spec {
    replicas = 1
    selector {
      match_labels = { "app.kubernetes.io/name" = "streaming-media-worker" }
    }
    template {
      metadata {
        labels      = { "app.kubernetes.io/name" = "streaming-media-worker" }
        annotations = { "streaming.platform/image-build" = null_resource.build_and_load_images.id }
      }
      spec {
        container {
          name              = "media-worker"
          image             = "streaming/media-worker:local"
          image_pull_policy = "Never"
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
            name = "MEDIA_STORAGE_ENDPOINT"
            value_from {
              config_map_key_ref {
                name = kubernetes_config_map.streaming_api_config.metadata[0].name
                key  = "media-storage-endpoint"
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
          env {
            name  = "MEDIA_WORKSPACE"
            value = "/tmp/streaming-media-worker"
          }
          env {
            name  = "MEDIA_WORKER_ID"
            value = "local-k8s-media-worker"
          }
          env {
            name  = "MEDIA_MAX_CONCURRENT_JOBS"
            value = "2"
          }
          volume_mount {
            name       = "worker-tmp"
            mount_path = "/tmp/streaming-media-worker"
          }
        }
        volume {
          name = "worker-tmp"
          empty_dir {}
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
