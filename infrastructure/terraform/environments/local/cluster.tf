locals {
  kind_config_yaml = <<-YAML
    kind: Cluster
    apiVersion: kind.x-k8s.io/v1alpha4
    nodes:
      - role: control-plane
        extraPortMappings:
          - containerPort: 30080
            hostPort: ${var.web_host_port}
            protocol: TCP
          - containerPort: 30030
            hostPort: ${var.grafana_host_port}
            protocol: TCP
          - containerPort: 30900
            hostPort: ${var.minio_host_port}
            protocol: TCP
  YAML
}

resource "local_file" "kind_config" {
  filename = "${path.module}/.generated/kind-config.yaml"
  content  = local.kind_config_yaml
}

resource "null_resource" "kind_cluster" {
  triggers = {
    cluster_name = var.cluster_name
    config_hash  = md5(local.kind_config_yaml)
    node_image   = var.kind_node_image
  }

  provisioner "local-exec" {
    command = <<-EOT
      set -euo pipefail
      if kind get clusters | grep -qx "${self.triggers.cluster_name}"; then
        echo "kind cluster ${self.triggers.cluster_name} already exists"
      else
        kind create cluster \
          --name "${self.triggers.cluster_name}" \
          --image "${self.triggers.node_image}" \
          --config "${local_file.kind_config.filename}" \
          --wait 180s
      fi
    EOT
  }

  provisioner "local-exec" {
    when    = destroy
    command = "kind delete cluster --name \"${self.triggers.cluster_name}\" || true"
  }

  depends_on = [local_file.kind_config]
}
