# Terraform

## `environments/local`

Real, runnable infrastructure-as-code targeting a local `kind` (Kubernetes-in-Docker) cluster — free, no cloud account, no credentials to manage. Provisions the full stack: the kind cluster itself, Postgres, MinIO (with the `media` bucket bootstrapped), api-gateway, media-worker, the web app, and a `kube-prometheus-stack` (Prometheus + Grafana) release scraping api-gateway's `/metrics`.

```bash
brew install kind opentofu kubernetes-cli helm   # one-time
cd infrastructure/terraform/environments/local
tofu init
tofu apply
```

Outputs give you the web app, Grafana, and `kubectl` context URLs/values. `tofu destroy` tears everything down (kind + kube-prometheus-stack are resource-heavy — don't leave it running when you're not using it).

Uses [OpenTofu](https://opentofu.org) (`tofu` binary), not HashiCorp Terraform — Terraform itself was pulled from homebrew-core after its license change (BSL). OpenTofu is an MPL-2.0, fully open-source, HCL-compatible fork; every `.tf` file here is ordinary Terraform-compatible syntax.

See [environments/local](environments/local) for the exact resources and a note on a `kubernetes` provider quirk you may hit if you edit the port-mapping variables (`tofu state rm` + delete the stuck object; documented inline in `images.tf`/`cluster.tf`).

## Why local, not AWS/GCP/Azure

Provisioning a real cloud account isn't something that can be done from here — creating an AWS/GCP/Azure/Oracle account needs a human to sign up and verify identity (and, even for a "free tier," usually a card for verification). `environments/local` is the free, fully-automatable path that's actually runnable today.

## Moving to a real cloud later

The `kubernetes` and `helm` resources in `environments/local` (Postgres, MinIO, api-gateway, web, media-worker, the monitoring stack) are not kind-specific — they're ordinary Kubernetes/Helm resources. Standing up a real cloud environment means:

1. A new `environments/<cloud>` directory with that cloud's provider block (e.g. `aws`, `google`, `azurerm`) provisioning a managed Kubernetes cluster (EKS/GKE/AKS), managed Postgres (RDS/Cloud SQL), object storage + CDN (S3+CloudFront, GCS+Cloud CDN, etc.), and a secrets manager.
2. Point the `kubernetes`/`helm` provider blocks at that cluster's kubeconfig instead of the local kind context.
3. Reuse the same Postgres/MinIO-or-S3/api-gateway/web/media-worker/monitoring resource definitions with minimal changes (swap MinIO for the cloud's native object storage, swap `random_password`-generated secrets for the cloud secrets manager).
4. Replace `images.tf`'s `docker build` + `kind load docker-image` with a real container registry push (ECR/GCR/ACR) and set `image_pull_policy` back to its default.

Credentials must be passed through a secret manager, never committed to `.tfvars` — `environments/local` already follows this (`random_password` resources generate secrets at apply time, nothing committed) and any new cloud environment should too.
