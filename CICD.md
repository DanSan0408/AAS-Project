# CI/CD, deployment, rollback and monitoring

IAMS has **one environment: production** (`https://iams.eduinnovax.com`). Changes are tested locally on localhost,
then go through CI and straight to production. There is no staging, so the safety net is automatic: tests gate the
build, the deploy verifies the new version answers before it is called done, and a failed deploy rolls itself back.

Production is three separate Dokploy services: the **app** (Application), the **AI service** (Application) and
**MySQL** (Database). The pipeline only ever touches the two Applications. **It never touches the database service.**

```
localhost  ->  pull request  ->  merge to main  ->  CI tests + publish images  ->  deploy to Dokploy  ->  smoke checks
                                                                                         |                     |
                                                                                         |                  fail: automatic
                                                                                         |                  rollback + Telegram
                                                                                      Telegram: success
```

## What runs when

| Trigger | Workflow | What it does |
|---|---|---|
| Pull request / push to `ihsan_branch` | `ci.yml` | Java tests, browser/static JS tests, AI service compile and unit tests, Docker builds; all five jobs run at the same time |
| Push to `main` | `build.yml` | The same tests, with both Docker images built at the same time (not pushed), then publishes immutable images `ghcr.io/dansan0408/aas-project-app:<git-sha>` and `aas-project-ai:<git-sha>` once the tests pass. The app image records its own git SHA |
| `build.yml` succeeded on `main` | `deploy.yml` | Deploys that exact SHA to production (never a commit whose tests failed) |
| Manual: Actions → Deploy → Run workflow | `deploy.yml` | Deploys any published tag. Use it to redeploy or to roll back on purpose |

**Why it is fast.** On `main` the tests and the two image builds start together (seven jobs, no waiting for each other).
The builds are not pushed; they fill the Docker layer cache. The publish jobs wait only for the tests, then re-run the same
build, which is now all cache hits, and push. So the wait is the slower of "tests" and "image build", not the sum, and an image
still only reaches the registry (and Deploy) when its tests passed. Further savings: the Maven dependencies are their own
Docker layer (a code-only change does not download them again), the image build no longer compiles the tests, and each image
has its own cache scope (before, the two images shared one and overwrote each other's cache). The first run after a merge
can be cold, because a branch's cache is not readable from `main` until `main` has written its own.

`deploy.yml` steps: resolve tag → confirm both images exist in GHCR → read the image each Dokploy Application runs now
(the rollback target) → point the AI service, then the app, at the new image and deploy
([Scripts/dokploy-image.sh](Scripts/dokploy-image.sh)) → smoke checks, including that `/actuator/info` reports the new
SHA (so old containers that are still answering cannot pass) → on failure, put the previous images back and check that
the failed SHA is gone → Telegram message with the outcome. `:latest` is refused everywhere.

## One-time setup

### 1. Make both Applications run published images
Today Dokploy probably builds the app from the repository. The pipeline instead runs the images CI already tested, so
that a rollback is just "run the previous image". Do this for the **app** and again for the **AI service**
(leave the MySQL service alone):

1. Dokploy → the Application → **General** → **Provider** → **Docker**.
   - Docker image: `ghcr.io/dansan0408/aas-project-app:<sha>` (AI service: `ghcr.io/dansan0408/aas-project-ai:<sha>`).
     Use a SHA from GitHub → Packages (ideally the commit production runs now; this seed is also what the first
     automatic rollback returns to).
   - GHCR must be readable by the server: either make the two packages public (GitHub → profile → Packages → package →
     Package settings → Change visibility), or enter registry URL `ghcr.io`, your GitHub username and a personal access
     token with `read:packages` as the registry login. The pipeline keeps whatever login you set.
2. Turn **Auto Deploy off** (GitHub Actions now decides when to deploy, after the tests pass), and remove or ignore the
   old per-service deploy webhooks.
3. **Keep the Environment and Domain tabs as they are.** The environment variables (database URL, mail, Telegram, ...)
   and the `iams.eduinnovax.com` domain belong to the Application and survive the change of source.
4. Click **Deploy** once and confirm the site works.

If an Application is not yet a Docker-image Application when the first pipeline run starts, there is no previous image
to return to; that run still deploys, but it reports "no rollback possible" if it fails.

### 2. Dokploy API access for GitHub
- API key: Dokploy → top-right profile → **Settings → Profile → API/CLI** → generate a token.
- Application ids: open the app; the id is the last part of the page URL (`.../services/application/<applicationId>`).
  Do the same for the AI service.
- `DOKPLOY_URL` is the address you open the Dokploy panel on. **Use an HTTPS address**: the API key is sent with every
  call, and `http://<ip>:3000` sends it in clear text. Give the panel a domain in Dokploy → Settings → Server → Domain.

### 3. GitHub secrets
GitHub → repository → **Settings → Environments → New environment → `production`**. Add these as **environment
secrets** (this replaces the old per-service webhook secrets, which are no longer used):

| Secret | Value |
|---|---|
| `DOKPLOY_URL` | Dokploy panel URL (HTTPS) |
| `DOKPLOY_API_KEY` | token from step 2 |
| `DOKPLOY_APP_ID` | application id of the app |
| `DOKPLOY_AI_ID` | application id of the AI service |
| `IAMS_APP_HEALTH_URL` | `https://iams.eduinnovax.com` |
| `IAMS_AI_HEALTH_URL` | optional. The AI service is only reachable inside Docker, so leave it empty: the app checks it itself, the AI service reports its own errors to Telegram, and the monitoring page shows it. Set it only if the AI health endpoint is reachable from GitHub |
| `TELEGRAM_BOT_TOKEN` | bot token from @BotFather |
| `TELEGRAM_DEPLOY_CHAT_ID` | chat id, see Telegram below |

Optional but recommended: in the `production` environment add **Required reviewers** (yourself). Every automatic
deploy then waits for one click on "Approve and deploy". Remove it if you want fully hands-off deploys.

### 4. Branch protection on `main`
Require pull requests, require the CI jobs to pass, block force pushes and direct pushes.

## Rolling back

1. **Automatic.** If the new version does not pass the smoke checks, the workflow puts the previous images back,
   redeploys and re-checks. You get a Telegram message saying which version is running again.
2. **Deliberate, from GitHub.** Actions → Deploy → Run workflow → enter the older SHA. Find SHAs in the list of
   successful Deploy runs, or in GitHub → Packages.
3. **From Dokploy, if GitHub is unavailable.** Each Application → **General** → Docker image → change the tag to the
   older SHA → **Deploy**. (Dokploy's own Deployments → Rollback feature also exists for Applications; this setup does
   not depend on it.)

### Rollbacks and the database (read this: it is a legacy system in continuous use)
A rollback replaces the code, not the database. So every release must stay compatible with the previous one's schema:

- **Add, never remove or rename, in the same release.** Add the new column (nullable or with a default), ship, and only
  in a later release stop using the old column. A rename or drop in one step breaks both the new release (if it must be
  rolled back) and the old one.
- A column the code stops mapping must be nullable. The startup schema guard (below) enforces this for you.
- Before a release that changes the schema in any other way, take a backup first (see below).

### Backups
The pipeline does not back up the database. In Dokploy open the MySQL **Database** service → **Backups** and schedule
a nightly backup to an S3-compatible destination (if your Dokploy version offers it), then **restore it once into a
scratch database** to prove it works. Uploaded PDFs live in the app's uploads volume (`/app/uploads`): make sure that
is a persistent volume or bind mount in the Application's **Advanced → Volumes**, and include it in your backups.

## Error management

- Every failure shows the user a page that matches what happened (404, 403, 500, ...), not a generic "System Error".
  Server faults also show a **reference** (e.g. `AB12CD34`). The same reference is on the log line and in the
  Super Admin monitoring page, so a screenshot from a user is enough to find the stack trace: search the Dokploy log for
  the reference in lowercase (it is the first 8 characters of the `requestId` on that log line).
- An expired session sends people to the login page with a notice; it is never shown as an error. Scripts and live
  progress streams get a plain `401` instead of an HTML page.
- Database/schema faults (unknown column, missing default value, missing table) are recognised and reported as their
  own incident with a clear user message, instead of a bare crash.

## Legacy database safety (schema guard)
At startup, after all other migration runners, the app compares the live MySQL schema with what the code maps:

- A column the code **no longer maps** that is `NOT NULL` with no default (the cause of "Field 'x' doesn't have a default
  value" on insert) is made **nullable**. Nothing is dropped and no data is changed. Turn the automatic fix off with
  `APP_SCHEMA_GUARD_AUTO_RELAX=false`.
- A column the code **needs but the database lacks** is reported (the monitoring page turns red and an alert is sent).
- Only tables the code maps are inspected. The guard can never stop the app from starting. Re-run it any time from
  **Super Admin → System Monitoring → Re-check schema**.

## Monitoring and alerts

**Super Admin → System Monitoring** (`/superadmin/monitoring`) shows: overall status; database, AI service, email and
upload-storage health; the database schema guard findings; memory, threads, load, disk, uptime and the deployed
version; error counters; the last 100 server errors with their references; and which alert channels are configured,
with a **Send a test alert** button.

The app also watches itself every minute and alerts on **changes** (down, warning, recovered), plus error spikes
(`ADPROJECT_ALERTS_THRESHOLD_CRITICAL_ERRORS` per minute) and any schema fault. It also sends a "started" message on
every boot, which doubles as proof that a deploy finished.

**Every error goes to Telegram** (2026-11-21). Any ERROR the app logs (failed requests, scheduled jobs, mail, AI calls,
database) is sent with the exception, the root cause, the first line of our code involved and the same reference the user
saw on the error page. The first error of each kind goes out at once; repeats within 5 minutes are counted and reported
afterwards, and at most 20 messages go out per 10 minutes (the next message says how many were held back), so a storm does
not bury the first message or get the bot throttled. Emails, passwords and tokens are masked. 4xx responses (404s, bots) are not
alerts, and neither are visitors closing the page. The app also says so when it shuts down gracefully. An unreachable AI service
is announced as DOWN.

**The AI service reports for itself** with the same bot and chat (`TELEGRAM_*` and `ADPROJECT_ENVIRONMENT_LABEL` are passed to
it in `docker-compose.yml`): Gemini failures (key, quota, outage), unhandled errors, start (including a refusal to start for
a bad secret) and stop. Only the kind of error is sent, never a prompt, a comment or an error message.

**The app cannot report that its own server is down**, so `.github/workflows/uptime.yml` checks production from outside every
5 minutes: the app (`/actuator/health`, which includes the database, so a database outage shows as DOWN) and, when
`IAMS_AI_HEALTH_URL` is set, the AI service. It messages the deploy chat once when something goes down, once an hour while it
stays down, and when it is back. GitHub's scheduler is best effort (a few minutes late is normal) and pauses after 60 days of
no repository activity. A second, independent probe such as UptimeRobot is still a good idea for the minutes GitHub is late.

**The database itself** cannot send Telegram messages. Three things cover it: the app's minute check (database DOWN or slow),
the uptime watcher above, and any database error the app logs. For container-level events (the MySQL container restarting or
being killed, disk or memory thresholds) turn on **Dokploy → Settings → Notifications → Telegram** with the same bot.

### Telegram setup
You already created the bot (BotFather). What is left is the chat id and putting the values where they are read.

1. Open Telegram, find your bot and press **Start** (a bot can only message a chat that has started it). For a team
   channel, add the bot to a group and send any message in it.
2. Get the chat id (use the real token; do not paste it anywhere public):
   `curl -s "https://api.telegram.org/bot<TOKEN>/getUpdates"` → in the JSON, `message.chat.id`. A personal chat is a
   positive number; a group is negative (often `-100...`). If the result is empty, send the bot another message and retry.
3. **GitHub** (deploy notifications): `production` environment secrets `TELEGRAM_BOT_TOKEN` and `TELEGRAM_DEPLOY_CHAT_ID`.
4. **Dokploy** (runtime alerts from the app): the app's **Environment** tab: `TELEGRAM_BOT_TOKEN`, and
   `TELEGRAM_ALERT_CHAT_ID` (or leave it out to reuse `TELEGRAM_DEPLOY_CHAT_ID`, which you should then also add).
   Redeploy.
5. Check it: Super Admin → System Monitoring → **Send a test alert**. The page also says whether Telegram is configured.

Local development does not need Telegram; leave the variables empty and alerts are simply skipped. If a token is ever
exposed, send `/revoke` to @BotFather and update both places.

## Environment variables used by the app

| Variable | Purpose |
|---|---|
| `APP_PUBLIC_BASE_URL` | Host used in emailed links. Production: `https://iams.eduinnovax.com` (now also the default) |
| `SPRING_MAIL_*`, `SPRING_MAIL_FROM` | Outgoing email; the monitoring page flags an invalid From address |
| `ADPROJECT_ALERTS_ENABLED`, `ADPROJECT_ALERTS_RECIPIENT_EMAIL`, `ADPROJECT_ALERTS_THRESHOLD_CRITICAL_ERRORS` | Email alerts |
| `TELEGRAM_BOT_TOKEN`, `TELEGRAM_ALERT_CHAT_ID`, `TELEGRAM_DEPLOY_CHAT_ID` | Telegram alerts |
| `ADPROJECT_ENVIRONMENT_LABEL` | Prefix on alert messages (default `production`) |
| `APP_SCHEMA_GUARD_AUTO_RELAX` | `false` disables the automatic nullable fix |
| `APP_REVISION` | Baked into the app image at build time (the git SHA); shown on the monitoring page and `/actuator/info`. Do not set it by hand |
