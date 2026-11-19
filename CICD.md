# CI/CD Pipeline

This repository uses GitHub Actions for validation, image publication, and guarded Dokploy deployment.

## Pipeline

### Pull request validation

Pull requests run independent jobs:

- Java Maven tests
- Browser/static JavaScript tests
- AI service Python compilation
- IAMS Docker image build
- AI Docker image build

Pull requests cannot merge until all jobs succeed.

### Main build

Merges to `main` run tests and publish immutable images to GitHub Container Registry:

- `ghcr.io/dansan0408/aas-project-app:<git-sha>`
- `ghcr.io/dansan0408/aas-project-ai:<git-sha>`

The workflows also publish `:latest`, but deployments must use an immutable SHA tag.

### Deployment

Use **Actions → Deploy → Run workflow**.

Select:

- `staging` or `production`
- A Git SHA or immutable release tag

Production deployments require a configured GitHub Environment approval/review policy.

## Required GitHub secrets

Configure these in the corresponding staging/production GitHub Environment.

### Dokploy deploy webhooks

- `DOKPLOY_APP_STAGING_WEBHOOK`
- `DOKPLOY_APP_PRODUCTION_WEBHOOK`
- `DOKPLOY_AI_STAGING_WEBHOOK`
- `DOKPLOY_AI_PRODUCTION_WEBHOOK`

### Health URLs

- `IAMS_APP_HEALTH_URL`
- `IAMS_AI_HEALTH_URL`

Examples:

- App health URL: `https://your-host.example/`
- AI health URL: `https://ai.your-host.example/health`

The app health endpoint is `/actuator/health`.

### Optional rollback webhooks

- `DOKPLOY_APP_STAGING_ROLLBACK_WEBHOOK`
- `DOKPLOY_APP_PRODUCTION_ROLLBACK_WEBHOOK`
- `DOKPLOY_AI_STAGING_ROLLBACK_WEBHOOK`
- `DOKPLOY_AI_PRODUCTION_ROLLBACK_WEBHOOK`

If a rollback secret is not configured, the workflow reports the smoke failure but cannot trigger that service automatically.

### Optional Telegram deployment notifications

- `TELEGRAM_BOT_TOKEN`
- `TELEGRAM_DEPLOY_CHAT_ID`

## Dokploy webhook responsibilities

Each app/AI deploy webhook must perform the deployment for the requested immutable image tag.

Production app deployment must also:

1. Back up MySQL.
2. Back up `/srv/iams/uploads`.
3. Pull the requested SHA-tagged app image.
4. Apply database migration/startup checks.
5. Replace or roll containers safely.
6. Preserve uploaded PDFs and database persistence.

Production AI deployment must pull the requested SHA-tagged AI image and restart only the AI service.

Dokploy webhooks should accept JSON:

```json
{
  "imageTag": "git-sha",
  "environment": "production",
  "commit": "git-sha"
}
```

Rollback webhooks should accept:

```json
{
  "environment": "production",
  "commit": "git-sha",
  "reason": "Smoke check failed"
}
```

## Smoke verification

The deployment workflow polls:

- `/login` on the IAMS application
- `/actuator/health` on the IAMS application
- `/health` on the AI service

The app and AI deployments run in parallel. Verification runs only after both webhook calls return successfully.

If verification fails, the workflow triggers configured rollback webhooks and sends Telegram deployment status where configured.

## Branch protection

Configure these rules on `main`:

- Require pull request review
- Require CI jobs
- Block direct pushes
- Block force pushes
- Require signed or verified commits when available
- Require at least one successful staging deployment before production approval
