resource "kubernetes_deployment_v1" "web" {
  metadata {
    name   = "streaming-web"
    labels = { "app.kubernetes.io/name" = "streaming-web" }
  }
  spec {
    replicas = 1
    selector {
      match_labels = { "app.kubernetes.io/name" = "streaming-web" }
    }
    template {
      metadata {
        labels      = { "app.kubernetes.io/name" = "streaming-web" }
        annotations = { "streaming.platform/image-build" = null_resource.build_and_load_images.id }
      }
      spec {
        container {
          name              = "web"
          image             = "streaming/web:local"
          image_pull_policy = "Never"
          port {
            name           = "http"
            container_port = 80
          }
          env {
            name  = "API_GATEWAY_HOST"
            value = kubernetes_service_v1.api_gateway.metadata[0].name
          }
          env {
            name  = "API_GATEWAY_PORT"
            value = "80"
          }
          readiness_probe {
            http_get {
              path = "/healthz"
              port = "http"
            }
            initial_delay_seconds = 5
            period_seconds        = 10
          }
          liveness_probe {
            http_get {
              path = "/healthz"
              port = "http"
            }
            initial_delay_seconds = 10
            period_seconds        = 20
          }
        }
      }
    }
  }
  depends_on = [null_resource.build_and_load_images, kubernetes_deployment_v1.api_gateway]
}

resource "kubernetes_service_v1" "web" {
  metadata {
    name = "streaming-web"
  }
  spec {
    type     = "NodePort"
    selector = { "app.kubernetes.io/name" = "streaming-web" }
    port {
      name        = "http"
      port        = 80
      target_port = "http"
      node_port   = 30080
    }
  }
  depends_on = [null_resource.kind_cluster]
}
