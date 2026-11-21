#!/usr/bin/env bash
# Reads or changes which image a Dokploy APPLICATION runs. Used to deploy a release and, with the previous image,
# to roll back. Needs DOKPLOY_URL and DOKPLOY_API_KEY in the environment.
#
#   Scripts/dokploy-image.sh get <applicationId>              prints the image the application runs now
#                                                             (nothing if it is not a Docker-image application)
#   Scripts/dokploy-image.sh set <applicationId> <image:tag>  points it at that image and starts a deploy
#
# The application's details include its environment (secrets) and registry password, so they are never printed and
# never put on a command line.
set -euo pipefail

: "${DOKPLOY_URL:?}" "${DOKPLOY_API_KEY:?}"
BASE="${DOKPLOY_URL%/}/api"
ACTION="${1:?usage: dokploy-image.sh get|set <applicationId> [image]}"
APP_ID="${2:?application id is required}"

fetch_application() {
  curl --fail --silent --show-error --max-time 30 -G \
    -H "x-api-key: $DOKPLOY_API_KEY" \
    --data-urlencode "applicationId=$APP_ID" \
    "$BASE/application.one"
}

post() { # post <endpoint>; JSON body on stdin
  curl --fail --silent --show-error --max-time 60 -X POST \
    -H "x-api-key: $DOKPLOY_API_KEY" -H "Content-Type: application/json" \
    --data-binary @- "$BASE/$1" > /dev/null
}

current_image() {
  fetch_application | jq -r 'if .sourceType == "docker" then (.dockerImage // "") else "" end'
}

case "$ACTION" in
  get)
    current_image
    ;;
  set)
    IMAGE="${3:?image (name:tag) is required}"
    if [[ ! "$IMAGE" =~ ^[a-z0-9._/-]+:[A-Za-z0-9._-]+$ || "$IMAGE" == *:latest ]]; then
      echo "Refusing image '$IMAGE': it must be name:tag with an immutable tag (not 'latest')." >&2
      exit 1
    fi
    # Keep the registry login that is already configured on the application.
    fetch_application | jq -c --arg id "$APP_ID" --arg image "$IMAGE" '{
        applicationId: $id, dockerImage: $image,
        username: (.username // null), password: (.password // null), registryUrl: (.registryUrl // null)}' \
      | post application.saveDockerProvider
    if [[ "$(current_image)" != "$IMAGE" ]]; then
      echo "Dokploy did not accept the new image for application $APP_ID." >&2
      exit 1
    fi
    jq -n --arg id "$APP_ID" --arg title "CI deploy ${IMAGE##*:}" '{applicationId: $id, title: $title}' \
      | post application.deploy
    echo "Dokploy deploy requested: $IMAGE"
    ;;
  *)
    echo "Unknown action '$ACTION' (use get or set)." >&2
    exit 1
    ;;
esac
