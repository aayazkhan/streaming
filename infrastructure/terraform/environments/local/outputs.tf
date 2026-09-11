output "web_url" {
  value       = "http://localhost:${var.web_host_port}"
  description = "The streaming web app."
}

output "grafana_url" {
  value       = "http://localhost:${var.grafana_host_port}"
  description = "Grafana. Default user is 'admin'; retrieve the generated password with: kubectl -n monitoring get secret kube-prometheus-stack-grafana -o jsonpath='{.data.admin-password}' | base64 -d"
}

output "minio_console_url" {
  value       = "MinIO API is exposed at http://localhost:${var.minio_host_port} (used internally by playback URLs); there is no console port mapped."
  description = "MinIO endpoint used by playback grants."
}

output "kubectl_context" {
  value       = "kind-${var.cluster_name}"
  description = "Pass this as --context to kubectl, or run `kubectl config use-context <this value>`."
}
