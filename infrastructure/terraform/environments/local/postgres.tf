resource "kubernetes_persistent_volume_claim" "postgres_data" {
  metadata {
    name = "streaming-postgres-data"
  }
  wait_until_bound = false
  spec {
    access_modes = ["ReadWriteOnce"]
    resources {
      requests = { storage = "1Gi" }
    }
  }
  depends_on = [null_resource.kind_cluster]
}

resource "kubernetes_deployment_v1" "postgres" {
  metadata {
    name   = "streaming-postgres"
    labels = { "app.kubernetes.io/name" = "streaming-postgres" }
  }
  spec {
    replicas = 1
    selector {
      match_labels = { "app.kubernetes.io/name" = "streaming-postgres" }
    }
    template {
      metadata {
        labels = { "app.kubernetes.io/name" = "streaming-postgres" }
      }
      spec {
        container {
          name  = "postgres"
          image = "postgres:16-alpine"
          port { container_port = 5432 }
          env {
            name  = "POSTGRES_DB"
            value = "streaming"
          }
          env {
            name  = "POSTGRES_USER"
            value = "streaming"
          }
          env {
            name = "POSTGRES_PASSWORD"
            value_from {
              secret_key_ref {
                name = kubernetes_secret.streaming_database.metadata[0].name
                key  = "password"
              }
            }
          }
          readiness_probe {
            exec {
              command = ["pg_isready", "-U", "streaming", "-d", "streaming"]
            }
            initial_delay_seconds = 5
            period_seconds        = 5
          }
          volume_mount {
            name       = "data"
            mount_path = "/var/lib/postgresql/data"
          }
        }
        volume {
          name = "data"
          persistent_volume_claim {
            claim_name = kubernetes_persistent_volume_claim.postgres_data.metadata[0].name
          }
        }
      }
    }
  }
  depends_on = [null_resource.kind_cluster]
}

resource "kubernetes_service_v1" "postgres" {
  metadata {
    name = "streaming-postgres"
  }
  spec {
    selector = { "app.kubernetes.io/name" = "streaming-postgres" }
    port {
      port        = 5432
      target_port = 5432
    }
  }
  depends_on = [null_resource.kind_cluster]
}
