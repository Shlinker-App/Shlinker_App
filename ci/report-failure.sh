#!/usr/bin/env bash
# Post the end of a failed job's log as an issue comment because the Forgejo API
# does not expose job logs.
set -uo pipefail
LOG="${1:-ci.log}"
ISSUE="${CI_LOG_ISSUE:-1}"
RUN_URL="${FORGEJO_SERVER_URL:-$GITHUB_SERVER_URL}/${GITHUB_REPOSITORY}/actions/runs/${GITHUB_RUN_NUMBER}"
TAIL=$( [ -f "$LOG" ] && tail -n 150 "$LOG" | cut -c1-400 || echo "(No log available: failure before the first Gradle step)")
BODY=$(RUN_URL="$RUN_URL" TAIL="$TAIL" node -e 'console.log(JSON.stringify({body:
  "**" + process.env.GITHUB_WORKFLOW + " failed**: [View run](" + process.env.RUN_URL + ")\n" +
  "Commit `" + process.env.GITHUB_SHA.slice(0,7) + "`, Job `" + process.env.GITHUB_JOB + "`\n\n```\n" + process.env.TAIL + "\n```"}))')
curl -fsS -X POST "${GITHUB_API_URL:-${GITHUB_SERVER_URL}/api/v1}/repos/${GITHUB_REPOSITORY}/issues/${ISSUE}/comments" \
  -H "Authorization: token ${TOKEN}" -H 'Content-Type: application/json' -d "$BODY" > /dev/null \
  && echo "Posted log tail to issue #${ISSUE}"
