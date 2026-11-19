#!/usr/bin/env bash
set -euo pipefail

APP_URL="${1:?App URL is required}"
AI_URL="${2:?AI URL is required}"

check_url() {
  local name="$1"
  local url="$2"
  local attempt

  for attempt in $(seq 1 30); do
    if curl --fail --silent --show-error --max-time 10 "$url" >/dev/null 2>&1; then
      echo "$name healthy: $url"
      return 0
    fi
    sleep 2
  done

  echo "$name failed health check: $url" >&2
  return 1
}

check_url "IAMS app" "${APP_URL%/}/login"
check_url "IAMS actuator" "${APP_URL%/}/actuator/health"
check_url "AI service" "${AI_URL%/}/health"
