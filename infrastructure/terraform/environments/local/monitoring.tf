resource "kubernetes_namespace" "monitoring" {
  metadata {
    name = "monitoring"
  }
  depends_on = [null_resource.kind_cluster]
}

# additionalScrapeConfigs (a static Prometheus scrape target) is used instead of a ServiceMonitor
# custom resource — ServiceMonitor requires the CRD to exist before the kubernetes provider's
# kubernetes_manifest resource can plan it, which forces an awkward two-phase `apply`. A static
# scrape config needs no CRD and works in a single apply.
resource "helm_release" "kube_prometheus_stack" {
  name       = "kube-prometheus-stack"
  repository = "https://prometheus-community.github.io/helm-charts"
  chart      = "kube-prometheus-stack"
  namespace  = kubernetes_namespace.monitoring.metadata[0].name

  values = [
    yamlencode({
      grafana = {
        service = {
          type     = "NodePort"
          nodePort = 30030
        }
        sidecar = {
          dashboards = {
            enabled         = true
            searchNamespace = kubernetes_namespace.monitoring.metadata[0].name
          }
        }
      }
      prometheus = {
        prometheusSpec = {
          additionalScrapeConfigs = [
            {
              job_name     = "streaming-api-gateway"
              metrics_path = "/metrics"
              static_configs = [
                { targets = ["${kubernetes_service_v1.api_gateway.metadata[0].name}.default.svc.cluster.local:80"] }
              ]
            }
          ]
        }
      }
    })
  ]

  depends_on = [kubernetes_namespace.monitoring, kubernetes_service_v1.api_gateway]
}

resource "kubernetes_config_map" "api_gateway_dashboard" {
  metadata {
    name      = "streaming-api-gateway-dashboard"
    namespace = kubernetes_namespace.monitoring.metadata[0].name
    labels = {
      grafana_dashboard = "1"
    }
  }

  data = {
    "streaming-api-gateway.json" = jsonencode({
      title   = "Streaming API Gateway"
      uid     = "streaming-api-gateway"
      schemaVersion = 39
      time    = { from = "now-1h", to = "now" }
      refresh = "10s"
      # kube-prometheus-stack's default (and only) Prometheus datasource always gets uid "prometheus"
      # (confirmed via GET /api/datasources on this cluster) — panels need this set explicitly,
      # otherwise a sidecar-provisioned dashboard's targets have no datasource bound and just render
      # blank instead of erroring.
      panels = [
        {
          id         = 1
          title      = "Request rate by route"
          type       = "timeseries"
          gridPos    = { h = 8, w = 12, x = 0, y = 0 }
          datasource = { type = "prometheus", uid = "prometheus" }
          targets = [
            {
              datasource   = { type = "prometheus", uid = "prometheus" }
              expr         = "sum(rate(ktor_http_server_requests_seconds_count{job=\"streaming-api-gateway\"}[1m])) by (route, method)"
              legendFormat = "{{method}} {{route}}"
            }
          ]
        },
        {
          id         = 2
          title      = "Latency (p50 / p95 / p99)"
          type       = "timeseries"
          gridPos    = { h = 8, w = 12, x = 12, y = 0 }
          datasource = { type = "prometheus", uid = "prometheus" }
          targets = [
            { datasource = { type = "prometheus", uid = "prometheus" }, expr = "histogram_quantile(0.50, sum(rate(ktor_http_server_requests_seconds_bucket{job=\"streaming-api-gateway\"}[5m])) by (le))", legendFormat = "p50" },
            { datasource = { type = "prometheus", uid = "prometheus" }, expr = "histogram_quantile(0.95, sum(rate(ktor_http_server_requests_seconds_bucket{job=\"streaming-api-gateway\"}[5m])) by (le))", legendFormat = "p95" },
            { datasource = { type = "prometheus", uid = "prometheus" }, expr = "histogram_quantile(0.99, sum(rate(ktor_http_server_requests_seconds_bucket{job=\"streaming-api-gateway\"}[5m])) by (le))", legendFormat = "p99" },
          ]
        },
        {
          id         = 3
          title      = "4xx / 5xx rate by status"
          type       = "timeseries"
          gridPos    = { h = 8, w = 12, x = 0, y = 8 }
          datasource = { type = "prometheus", uid = "prometheus" }
          targets = [
            {
              datasource   = { type = "prometheus", uid = "prometheus" }
              expr         = "sum(rate(ktor_http_server_requests_seconds_count{job=\"streaming-api-gateway\", status=~\"4..|5..\"}[1m])) by (status)"
              legendFormat = "{{status}}"
            }
          ]
        },
      ]
    })
  }

  depends_on = [kubernetes_namespace.monitoring]
}
