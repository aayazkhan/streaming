resource "kubernetes_persistent_volume_claim" "minio_data" {
  metadata {
    name = "streaming-minio-data"
  }
  wait_until_bound = false
  spec {
    access_modes = ["ReadWriteOnce"]
    resources {
      requests = { storage = "2Gi" }
    }
  }
  depends_on = [null_resource.kind_cluster]
}

resource "kubernetes_deployment_v1" "minio" {
  metadata {
    name   = "streaming-minio"
    labels = { "app.kubernetes.io/name" = "streaming-minio" }
  }
  spec {
    replicas = 1
    selector {
      match_labels = { "app.kubernetes.io/name" = "streaming-minio" }
    }
    template {
      metadata {
        labels = { "app.kubernetes.io/name" = "streaming-minio" }
      }
      spec {
        container {
          name  = "minio"
          image = "minio/minio:RELEASE.2025-02-28T09-55-16Z"
          # `args`, not `command` — Kubernetes' `command` overrides the image's ENTRYPOINT
          # (docker-entrypoint.sh, which invokes the real `minio` binary), unlike Docker
          # Compose's `command:` which only sets CMD appended after the entrypoint.
          args = ["server", "/data", "--console-address", ":9001"]
          port { container_port = 9000 }
          port { container_port = 9001 }
          env {
            name = "MINIO_ROOT_USER"
            value_from {
              secret_key_ref {
                name = kubernetes_secret.streaming_media_storage.metadata[0].name
                key  = "access-key"
              }
            }
          }
          env {
            name = "MINIO_ROOT_PASSWORD"
            value_from {
              secret_key_ref {
                name = kubernetes_secret.streaming_media_storage.metadata[0].name
                key  = "secret-key"
              }
            }
          }
          volume_mount {
            name       = "data"
            mount_path = "/data"
          }
        }
        volume {
          name = "data"
          persistent_volume_claim {
            claim_name = kubernetes_persistent_volume_claim.minio_data.metadata[0].name
          }
        }
      }
    }
  }
  depends_on = [null_resource.kind_cluster]
}

resource "kubernetes_service_v1" "minio" {
  metadata {
    name = "streaming-minio"
  }
  spec {
    type     = "NodePort"
    selector = { "app.kubernetes.io/name" = "streaming-minio" }
    port {
      port        = 9000
      target_port = 9000
      node_port   = 30900
    }
  }
  depends_on = [null_resource.kind_cluster]
}

# One-time bucket bootstrap: create the "media" bucket and allow anonymous download, mirroring the
# manual `mc` steps used earlier for the docker-compose stack, so playback URLs are directly
# fetchable by the browser without a real CDN. Runs as a Kubernetes Job so it executes inside the
# cluster network; fire-and-forget is fine since api-gateway/media-worker retry their own MinIO calls.
resource "kubernetes_job_v1" "minio_bootstrap" {
  metadata {
    name = "streaming-minio-bootstrap"
  }
  spec {
    template {
      metadata {
        labels = { "app.kubernetes.io/name" = "streaming-minio-bootstrap" }
      }
      spec {
        restart_policy = "OnFailure"
        container {
          name    = "mc"
          image   = "minio/mc:latest"
          command = ["sh", "-c"]
          args = [
            <<-EOT
              set -e
              until mc alias set local http://streaming-minio:9000 "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD"; do sleep 2; done
              mc mb --ignore-existing local/media
              mc anonymous set download local/media
            EOT
          ]
          env {
            name = "MINIO_ROOT_USER"
            value_from {
              secret_key_ref {
                name = kubernetes_secret.streaming_media_storage.metadata[0].name
                key  = "access-key"
              }
            }
          }
          env {
            name = "MINIO_ROOT_PASSWORD"
            value_from {
              secret_key_ref {
                name = kubernetes_secret.streaming_media_storage.metadata[0].name
                key  = "secret-key"
              }
            }
          }
        }
      }
    }
    backoff_limit = 6
  }
  wait_for_completion = true
  timeouts {
    create = "3m"
  }
  depends_on = [kubernetes_deployment_v1.minio, kubernetes_service_v1.minio]
}
