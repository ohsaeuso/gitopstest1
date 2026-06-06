---
name: observability
description: Project observability conventions. Consult before adding logs,
  custom metrics, or instrumenting distributed tracing.
---

# Skill: Observability

## Logs

- SLF4J + Logback. JSON encoder via `logstash-logback-encoder`.
- Message pattern: `<action>.<entity>.<result> <key>=<value> ...`
  Example: `cart.item.added cartId=abc productId=xyz quantity=2`
- INFO level for business events (creation, update, deletion).
- WARN level for recoverable conditions (retry, fallback active).
- ERROR level only for things requiring human attention.
- NEVER log sensitive data (SSN, card, password, token). Use placeholders.

## Metrics (Micrometer + Prometheus)

- Exposed via `/actuator/prometheus`.
- Custom metrics use `<domain>_<action>_<unit>`.
  Example: `cart_items_added_total` (counter), `checkout_duration_seconds` (timer).
- Mandatory tags: `environment`, `service`. Optional: `customer_tier`.

## Distributed tracing (OpenTelemetry)

- Auto-instrumentation via `opentelemetry-spring-boot-starter`.
- Manual spans only at critical business points (checkout, payment).
- NEVER propagate trace headers in logs (already automatic via MDC).