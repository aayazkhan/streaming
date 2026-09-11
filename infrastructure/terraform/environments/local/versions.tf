# OpenTofu (MPL-2.0, homebrew-core `opentofu` formula) is used instead of HashiCorp Terraform —
# Terraform itself moved to a BSL license and was pulled from homebrew-core. OpenTofu is a drop-in,
# HCL-compatible fork; every file in this directory is ordinary Terraform-compatible syntax and
# would work unmodified with `terraform` too if you have it installed via another source.

terraform {
  required_version = ">= 1.6.0"

  required_providers {
    null = {
      source  = "hashicorp/null"
      version = "~> 3.2"
    }
    local = {
      source  = "hashicorp/local"
      version = "~> 2.5"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.6"
    }
    kubernetes = {
      source  = "hashicorp/kubernetes"
      version = "~> 2.31"
    }
    helm = {
      source  = "hashicorp/helm"
      version = "~> 2.14"
    }
  }
}

# The cluster is created by the null_resource in cluster.tf via the `kind` CLI (not the community
# `tehcyx/kind` Terraform provider) so this stays correct against whatever kind CLI version is
# installed, rather than depending on a smaller provider's HCL schema staying stable. `kind create
# cluster --name <name>` always writes to the default kubeconfig (~/.kube/config) under a
# deterministic context name `kind-<name>` — both are static strings known at plan time, so the
# providers below can reference them directly without needing a computed attribute from that
# resource. Every resource that actually talks to the cluster still declares an explicit
# `depends_on = [null_resource.kind_cluster]` so apply ordering is correct in a single `tofu apply`.
provider "kubernetes" {
  config_path    = "~/.kube/config"
  config_context = "kind-${var.cluster_name}"
}

provider "helm" {
  kubernetes {
    config_path    = "~/.kube/config"
    config_context = "kind-${var.cluster_name}"
  }
}
