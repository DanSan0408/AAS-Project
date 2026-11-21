#!/usr/bin/env bash
# Polls the freshly deployed stack until it is healthy, or fails.
#
#   Scripts/deployment-smoke.sh <app-url> [ai-health-url]
#
# The AI service is usually reachable only inside Docker, so its URL is optional (the app's own monitoring checks it
# and alerts on Telegram).
#
# EXPECTED_REVISION (optional): the image tag that must be running. When set, /actuator/info has to report it, so the
# check cannot pass on the OLD containers that are still answering while the new ones start.
# UNEXPECTED_REVISION (optional): used after a rollback. /actuator/info must NOT report this one (the release that failed),
# which proves the old version is the one answering even if it was built before versions were recorded.
# SMOKE_ATTEMPTS / SMOKE_INTERVAL tune the wait (default 60 x 5s = 5 minutes).
set -euo pipefail

APP_URL="${1:?App URL is required}"
AI_URL="${2:-}"
ATTEMPTS="${SMOKE_ATTEMPTS:-60}"
INTERVAL="${SMOKE_INTERVAL:-5}"

wait_for() {
  local name="$1" url="$2" expected="${3:-}" forbidden="${4:-}" attempt body
  for attempt in $(seq 1 "$ATTEMPTS"); do
    if body=$(curl --fail --silent --show-error --max-time 10 "$url" 2>/dev/null); then
      if [[ ( -z "$expected" || "$body" == *"$expected"* ) && ( -z "$forbidden" || "$body" != *"$forbidden"* ) ]]; then
        echo "$name healthy: $url"
        return 0
      fi
    fi
    sleep "$INTERVAL"
  done
  echo "$name failed health check: $url${expected:+ (expected to contain: $expected)}${forbidden:+ (must not contain: $forbidden)}" >&2
  return 1
}

wait_for "IAMS app" "${APP_URL%/}/login"
wait_for "IAMS actuator" "${APP_URL%/}/actuator/health" '"status":"UP"'
if [[ -n "${EXPECTED_REVISION:-}" ]]; then
  wait_for "IAMS version" "${APP_URL%/}/actuator/info" "$EXPECTED_REVISION"
fi
if [[ -n "${UNEXPECTED_REVISION:-}" ]]; then
  wait_for "IAMS version" "${APP_URL%/}/actuator/info" "" "$UNEXPECTED_REVISION"
fi
if [[ -n "$AI_URL" ]]; then
  wait_for "AI service" "${AI_URL%/}/health"
fi
