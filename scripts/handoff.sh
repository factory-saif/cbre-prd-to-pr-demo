#!/usr/bin/env bash
# Starts the next pipeline stage by POSTing to its Factory webhook automation.
# Usage: scripts/handoff.sh <dev|review|qa> '<json body>'
# URLs and secrets live only on the Droid Computer, never in this repository.
set -euo pipefail

stage="${1:?stage required: dev, review, or qa}"
body="${2:?json body required}"
env_file="${DEMO_WEBHOOKS_ENV:-$HOME/.config/prd-to-pr-demo/webhooks.env}"

if [[ ! -f "$env_file" ]]; then
  echo "handoff: $env_file not found on this computer" >&2
  exit 2
fi
# shellcheck disable=SC1090
source "$env_file"

case "$stage" in
  dev) url="${DEV_WEBHOOK_URL:-}" secret="${DEV_WEBHOOK_SECRET:-}" ;;
  review) url="${REVIEW_WEBHOOK_URL:-}" secret="${REVIEW_WEBHOOK_SECRET:-}" ;;
  qa) url="${QA_WEBHOOK_URL:-}" secret="${QA_WEBHOOK_SECRET:-}" ;;
  *) echo "handoff: unknown stage $stage" >&2; exit 2 ;;
esac

if [[ -z "$url" || -z "$secret" ]]; then
  echo "handoff: URL or secret for $stage is missing in $env_file" >&2
  exit 2
fi

status=$(curl -sS -o /dev/null -w '%{http_code}' --retry 2 -X POST "$url" \
  -H "X-Webhook-Secret: $secret" \
  -H 'Content-Type: application/json' \
  --data "$body")
echo "handoff: $stage responded HTTP $status"
[[ "$status" =~ ^2 ]]
