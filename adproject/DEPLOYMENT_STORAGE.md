# Persistent report storage on a VPS

## Dokploy: separate app and database services

This is the setup for an application deployed independently from its database.
Configure upload storage on the **app service only**. Keep the existing database
service, datasource URL and credentials. Do not deploy the repository's combined
Compose stack over those services.

The Dockerfile prepares `/app/uploads/submissions`, assigns it to `appuser`, and
sets `APP_UPLOAD_SUBMISSIONS_DIR=/app/uploads/submissions` independently of Compose.
A persistent mount must still be configured in Dokploy; rebuilding the image does
not create that mount for you.

### 1. Preserve existing reports and prepare ownership

Before adding the mount or redeploying an app that already holds uploaded PDFs,
identify its current container using `docker ps` on the VPS. During migration,
prevent new submissions so files and database references remain consistent.
Use the **app** container, not the MySQL container:

```bash
APP_CONTAINER=your-current-dokploy-app-container
APP_UPLOAD_UID=$(docker exec "$APP_CONTAINER" id -u appuser)
APP_UPLOAD_GID=$(docker exec "$APP_CONTAINER" id -g appuser)
sudo mkdir -p /srv/iams/uploads/submissions
```

If existing reports are inside that container, copy them out before replacement:

```bash
mkdir -p /tmp/iams-dokploy-submission-backup
docker cp "$APP_CONTAINER":/app/uploads/submissions/. /tmp/iams-dokploy-submission-backup/
sudo rsync -a /tmp/iams-dokploy-submission-backup/ /srv/iams/uploads/submissions/
```

For a fresh deployment, skip the copy. For migration from a laptop/another server,
copy the old `uploads/submissions/` contents to the same host path instead.
If the old app has a custom upload root, copy from that root.

Initialise permissions for the app user identified above:

```bash
sudo chown -R "$APP_UPLOAD_UID:$APP_UPLOAD_GID" /srv/iams/uploads
```

If there is no running app yet, use its built Docker image to determine the user:
`docker run --rm --network none --entrypoint id YOUR_APP_IMAGE appuser`.
Use the reported numeric UID/GID for ownership. Check ownership again if a later
image changes the app user's UID/GID; bind mounts retain host ownership.

### 2. Add a bind mount to the app service

In Dokploy, open the **app service → Advanced → Volumes/Mounts** and add:

| Setting | Value |
| --- | --- |
| Type | Bind Mount |
| Host Path | `/srv/iams/uploads` |
| Mount Path | `/app/uploads` |

In the app service's environment, set:

```dotenv
APP_UPLOAD_SUBMISSIONS_DIR=/app/uploads/submissions
```

`APP_UPLOAD_HOST_DIR` is a Compose interpolation setting and does not create a
Dokploy application mount. Configure the host path in Dokploy's mount settings.
Use a directory outside Dokploy's repository checkout so redeployments cannot
replace the storage directory. For multiple deployment nodes, keep this app on
the storage node or provide shared storage.

### 3. Redeploy only the app and verify

After copying reports and preparing ownership, save the mount/environment and
redeploy the app through Dokploy. Keep the database service running as configured.
Verify the mounted directory as the normal app user in the new app container:

```bash
APP_CONTAINER=your-new-dokploy-app-container
docker exec "$APP_CONTAINER" sh -c 'test -w /app/uploads/submissions && echo "Submission storage is writable"'
```

Open an existing report, verify its doodles, upload a new report, then redeploy the
app again and confirm the report remains available. Back up the host upload
directory and the separate database together. The upload mount contains PDFs;
submission records and doodles remain in the database service.

Dokploy reference: [application volumes/mounts](https://docs.dokploy.com/docs/core/applications/advanced#volumesmounts).

## Alternative: standalone Docker Compose deployment

The commands below are for the repository's combined Compose setup, **not** the
separate Dokploy app/database deployment above.

Run the commands below from `adproject/` on your Linux VPS. Compose uses:

| Location | Path |
| --- | --- |
| VPS host directory | `/srv/iams/uploads` by default |
| Directory mounted inside the app container | `/app/uploads` |
| PDF submission root inside the container | `/app/uploads/submissions` |

For example, a report stored as `course_16/assessment_52/student_95/sub_....pdf`
is physically located at
`/srv/iams/uploads/submissions/course_16/assessment_52/student_95/sub_....pdf`.
Database paths are relative to the submission root, so changing the host directory
does not require rewriting submission records.

### First Compose deployment

1. Configure your existing credentials in `.env`. The optional host directory setting is:

   ```dotenv
   APP_UPLOAD_HOST_DIR=/srv/iams/uploads
   ```

   `APP_UPLOAD_SUBMISSIONS_DIR` is set by Compose to `/app/uploads/submissions`.
   To change the VPS storage location, change `APP_UPLOAD_HOST_DIR` instead.
   For local Docker development, `APP_UPLOAD_HOST_DIR=./uploads` mounts the
   existing `adproject/uploads/` directory.

2. Create the host directory. Use your chosen path if you changed the default:

   ```bash
   sudo mkdir -p /srv/iams/uploads/submissions
   ```

   The bind mount deliberately does not auto-create a missing host directory.
   This catches an incorrect storage path before the application starts.

3. If migrating existing PDFs, copy them as described below before continuing.
   Build the app image and initialise ownership using the image's actual app user:

   ```bash
   docker compose build app
   docker compose run --rm --no-deps --user root --entrypoint sh app \
     -c 'mkdir -p /app/uploads/submissions && chown -R appuser:appuser /app/uploads'
   ```

   This one-time setup command runs as root to set directory ownership. The
   application itself continues running as the non-root `appuser`. Repeat the
   ownership step after importing files or selecting a different host directory.

4. Check access as the normal app user, then start the system:

   ```bash
   docker compose run --rm --no-deps --entrypoint sh app \
     -c 'test -w /app/uploads/submissions && echo "Submission storage is writable"'
   docker compose up -d
   ```

### Migrating reports for Compose

Back up the database and PDFs together. The database contains submission paths,
metadata and assessor doodles; the upload directory contains the original PDFs.
An imported database alone does not restore the documents.

#### From a local checkout or another server

Copy the **contents** of the old `uploads/submissions/` into the new host's
`/srv/iams/uploads/submissions/`, preserving the course, assessment and target
subdirectories. For example, once the files are available on the VPS:

```bash
sudo rsync -a /path/to/old/uploads/submissions/ /srv/iams/uploads/submissions/
```

Then initialise ownership and check access using the commands above. Do not add
an extra nested `submissions/submissions/` directory.

#### From an existing container without the upload mount

Do this **before recreating or removing the old app container**. Stop uploads
while copying, and retain the backup until you have verified the migration:

```bash
docker compose stop app
mkdir -p /tmp/iams-submission-backup
docker cp adproject-app:/app/uploads/submissions/. /tmp/iams-submission-backup/
sudo mkdir -p /srv/iams/uploads/submissions
sudo rsync -a /tmp/iams-submission-backup/ /srv/iams/uploads/submissions/
```

If the old deployment used a custom submission root, copy from that path instead.
Then follow the build, ownership and access-check steps before starting the new
container. Verify existing reports and doodles through the application.

## Redeployment and backups

- Rebuilding or replacing the app container keeps uploaded reports in the host directory.
- Keep `APP_UPLOAD_HOST_DIR` pointing at the same directory across redeployments.
- Back up `/srv/iams/uploads/` (or your custom directory) together with MySQL.
- A bind mount preserves files across container replacement; it is not a backup
  against VPS disk failure or deletion of the host directory.
- PDF replacements still follow the application's existing behaviour: the old
  PDF is deleted and the latest submission replaces it.
