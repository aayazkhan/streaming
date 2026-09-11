# Monitoring

## What's real now

`api-gateway` exposes `/metrics` (Prometheus/Micrometer, via Ktor's `MicrometerMetrics` plugin — see `Application.kt`), publishing percentile histograms so `histogram_quantile` queries work, not just counters. `infrastructure/terraform/environments/local` provisions a `kube-prometheus-stack` (Prometheus + Grafana) release that scrapes it and auto-loads a dashboard (`monitoring.tf`'s `api_gateway_dashboard` config map) covering:

- **request rate by route** — `sum(rate(ktor_http_server_requests_seconds_count{job="streaming-api-gateway"}[1m])) by (route, method)`
- **p50/p95/p99 latency** — `histogram_quantile(0.95, sum(rate(ktor_http_server_requests_seconds_bucket{...}[5m])) by (le))` (same pattern for p50/p99)
- **4xx/5xx rate by status** — `sum(rate(ktor_http_server_requests_seconds_count{..., status=~"4..|5.."}[1m])) by (status)`

Verified end-to-end against real traffic (registered a user, browsed the catalog through the k8s-deployed web app, watched the panels populate in Grafana) — not just wired and unverified.

Bring it up with `cd infrastructure/terraform/environments/local && tofu apply`, then open the `grafana_url` output. Default user is `admin`; get the generated password with:

```bash
kubectl -n monitoring get secret kube-prometheus-stack-grafana -o jsonpath='{.data.admin-password}' | base64 -d
```

`kube-prometheus-stack` also ships its own default dashboards (node/pod CPU/memory, cluster overview) for free — the custom dashboard above is on top of those, specific to the application's own request metrics.

## Still manual / not yet covered

The other three signal groups called for in the original Phase 1 spec are **not** wired up yet — don't assume they exist:

- **Database pool utilization, connection wait time, readiness failures** — HikariCP exposes Micrometer metrics natively; they aren't registered against `prometheusRegistry` yet (only the HTTP-server metrics are).
- **Auth failure / refresh-token replay / device-mismatch / registration-conflict alerting** — no Prometheus alerting rules exist for these; they'd need either custom counters in the identity service or log-based alerting.
- **Pod restarts, CPU/memory saturation, rollout availability** — actually already covered "for free" by `kube-prometheus-stack`'s bundled dashboards/`kube-state-metrics`/`node-exporter`, but no custom alert rules have been written against them yet.

No credentials or raw access/refresh tokens belong in logs — unchanged from the original policy, still true, not re-verified as part of this pass.
