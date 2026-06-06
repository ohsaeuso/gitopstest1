---
description: Tracks a deployment in progress and reports status
argument-hint: <env> (staging|dev) [<commit-sha>]
allowed-tools: Bash(argocd:*), Bash(kubectl:*), Bash(curl:*)
---

You will monitor the deploy of **$ARGUMENTS** and report status in real time.

1. **Identify the corresponding ArgoCD application:**
    - staging → ecommerce-api-staging
    - dev → ecommerce-api-dev

2. **Sync polling** (5min timeout):
   ​```bash
   argocd app wait gitopstest1-app --sync --timeout 300
   ​```

3. **Check pod health:**
   ​```bash
   kubectl -n gitopstest1 get pods -l app=gitopstest1-app
   kubectl -n gitopstest1 logs -l app=gitopstest1-app --tail=50
   ​```

4. **Smoke test on health endpoint:**
   ​```bash
   curl https://api-staging.example.com/actuator/health
   ​```

5. **Report:**
    - Final deploy status (Synced / Failed / OutOfSync)
    - Number of healthy pods vs total
    - Notable errors in logs (last 50 lines)
    - Latency of the health response

**IMPORTANT LIMITS:**
- NEVER run `argocd app sync` manually. Sync goes through PR in the deploy repo.
- NEVER run `kubectl delete pod` or rollback. Observation only.
- NEVER touch production. This command is for `dev` and `staging` only.
- If something is clearly broken, REPORT and stop. Decision is the dev's.