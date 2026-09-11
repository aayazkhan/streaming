variable "cluster_name" {
  description = "Name of the local kind cluster. The kubeconfig context is always `kind-<this>`."
  type        = string
  default     = "streaming-local"
}

variable "kind_node_image" {
  description = "kind node image (pins the Kubernetes version of the local cluster)."
  type        = string
  default     = "kindest/node:v1.31.0"
}

variable "web_host_port" {
  description = "Host port (on this machine) mapped to the web app's NodePort. Pick a port that's actually free on your machine — check with `lsof -iTCP:<port> -sTCP:LISTEN`."
  type        = number
  default     = 18090
}

variable "grafana_host_port" {
  description = "Host port mapped to Grafana's NodePort."
  type        = number
  default     = 18091
}

variable "minio_host_port" {
  description = "Host port mapped to MinIO's NodePort (needed so the browser can fetch playback segments directly, mirroring the docker-compose CDN_BASE_URL setup)."
  type        = number
  default     = 19090
}

variable "repo_root" {
  description = "Absolute path to the repository root, used for docker build contexts."
  type        = string
  default     = "../../../.."
}
