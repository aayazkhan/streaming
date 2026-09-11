# Builds the same Dockerfiles used by infrastructure/docker/docker-compose.yml and apps/webApp/Dockerfile,
# then loads them straight into the kind cluster's node (kind clusters can't pull from the host's
# local Docker image cache, only from a registry or via `kind load docker-image`). Rebuilds happen
# on every apply — cheap thanks to Docker layer caching, and simpler than trying to hash the whole
# build context to detect source changes.

resource "null_resource" "build_and_load_images" {
  triggers = {
    dockerfiles_hash = md5(join("", [
      filemd5("${var.repo_root}/infrastructure/docker/api-gateway.Dockerfile"),
      filemd5("${var.repo_root}/infrastructure/docker/media-worker.Dockerfile"),
      filemd5("${var.repo_root}/apps/webApp/Dockerfile"),
    ]))
    # Re-run whenever the cluster itself was recreated (e.g. a port mapping change forces a
    # destroy/create of null_resource.kind_cluster) — a fresh kind node starts with no images
    # loaded at all, so this can't be gated on Dockerfile content alone.
    cluster_id = null_resource.kind_cluster.id
    # Bump this manually (or `tofu apply -replace=null_resource.build_and_load_images`) after
    # changing app source without touching a Dockerfile — Terraform has no built-in recursive
    # directory hash, and hashing the whole build context here isn't worth the complexity for a
    # local dev tool where a manual rebuild trigger is a one-line command.
    rebuild_nonce = "1"
  }

  provisioner "local-exec" {
    working_dir = path.module
    command     = <<-EOT
      set -euo pipefail
      docker build -t streaming/api-gateway:local -f "${var.repo_root}/infrastructure/docker/api-gateway.Dockerfile" "${var.repo_root}"
      docker build -t streaming/media-worker:local -f "${var.repo_root}/infrastructure/docker/media-worker.Dockerfile" "${var.repo_root}"
      docker build -t streaming/web:local -f "${var.repo_root}/apps/webApp/Dockerfile" "${var.repo_root}/apps/webApp"
      kind load docker-image streaming/api-gateway:local streaming/media-worker:local streaming/web:local --name "${var.cluster_name}"
    EOT
  }

  depends_on = [null_resource.kind_cluster]
}
