#!/usr/bin/env bash
# Postet bei einem fehlgeschlagenen Lauf das Log-Ende als Kommentar in Issue #1,
# weil die Forgejo-API keinen Zugriff auf Job-Logs bietet.
set -uo pipefail
LOG="${1:-ci.log}"
ISSUE="${CI_LOG_ISSUE:-1}"
RUN_URL="${FORGEJO_SERVER_URL:-$GITHUB_SERVER_URL}/${GITHUB_REPOSITORY}/actions/runs/${GITHUB_RUN_NUMBER}"
TAIL=$( [ -f "$LOG" ] && tail -n 150 "$LOG" | cut -c1-400 || echo "(kein Log vorhanden – Fehler vor dem ersten Gradle-Schritt)")
BODY=$(RUN_URL="$RUN_URL" TAIL="$TAIL" node -e 'console.log(JSON.stringify({body:
  "**" + process.env.GITHUB_WORKFLOW + " fehlgeschlagen** – [Lauf öffnen](" + process.env.RUN_URL + ")\n" +
  "Commit `" + process.env.GITHUB_SHA.slice(0,7) + "`, Job `" + process.env.GITHUB_JOB + "`\n\n```\n" + process.env.TAIL + "\n```"}))')
curl -fsS -X POST "${GITHUB_API_URL:-${GITHUB_SERVER_URL}/api/v1}/repos/${GITHUB_REPOSITORY}/issues/${ISSUE}/comments" \
  -H "Authorization: token ${TOKEN}" -H 'Content-Type: application/json' -d "$BODY" > /dev/null \
  && echo "Log-Ende in Issue #${ISSUE} gepostet"
