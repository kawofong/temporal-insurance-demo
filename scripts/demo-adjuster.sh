#!/usr/bin/env bash
# End-to-end demo driver for the property-claim AI-adjuster "drain" scenario (spec §6.5): one
# Temporal batch signal flips every *running* property claim to AI adjustment, so claims parked
# on a human adjuster are handed to the field- and claim-adjuster agents and drain to CLOSED.
#
# This fires a single enableAiAdjuster batch operation over a Visibility query
# (ExecutionStatus='Running') rather than a client-side list-and-loop, so it scales to the
# 10k-claim CAT case. Seed the claims first (e.g. via a CATEventWorkflow or the portal) — this
# script signals whatever is already running.
#
# Prerequisites (separate terminals): `mise run temporal:dev`, `mise run temporal:worker`,
# `mise run api`, and `mise run agents:worker` (needs Ollama) for the agents to close the claims.
set -euo pipefail

API_BASE_URL="${API_BASE_URL:-http://localhost:8080}"
CLAIMS_URL="${API_BASE_URL}/api/v1/claims/property"
POLL_TIMEOUT="${POLL_TIMEOUT:-180}"    # seconds to wait for the queue to drain (LLM paths are slow)

usage() {
    cat <<'EOF'
Usage: demo-adjuster.sh

Batch-signals enableAiAdjuster to every running property claim and watches the claims parked on
a human adjuster drain to CLOSED.

  -h, --help            Show this help.

Env overrides: API_BASE_URL (default http://localhost:8080), POLL_TIMEOUT (seconds).
EOF
}

# Extracts a top-level string field from a small JSON object without a jq dependency.
json_field() {
    local field="$1" json="$2"
    grep -o "\"${field}\":\"[^\"]*\"" <<<"${json}" | head -1 | sed -E "s/\"${field}\":\"([^\"]*)\"/\1/"
}

require_api() {
    if ! curl -s -o /dev/null --connect-timeout 3 "${API_BASE_URL}"; then
        echo "Error: API not reachable at ${API_BASE_URL}. Start it with 'mise run api'." >&2
        exit 1
    fi
}

# Counts property claims currently in a given status via the list (Visibility) endpoint. The
# demo's backlog fits one page; a production-scale drain would page through nextPageToken.
count_by_status() {
    local status="$1" response
    response=$(curl -s "${CLAIMS_URL}?status=${status}&pageSize=1000")
    grep -o '"claimId"' <<<"${response}" | wc -l | tr -d ' '
}

# Total claims still parked on a human adjuster seam (either pending status). A drained claim
# has left both — CLOSED on approval or REJECTED on denial.
count_pending() {
    echo $(( $(count_by_status PENDING_DAMAGE_ASSESSMENT) + $(count_by_status PENDING_APPROVAL) ))
}

demo_drain() {
    require_api

    local before
    before=$(count_pending)
    echo "==> ${before} property claim(s) currently parked on a human adjuster."
    if [[ "${before}" -eq 0 ]]; then
        echo "    Nothing parked to drain — seed some pending claims first, then re-run." >&2
    fi

    echo "==> Batch-signalling enableAiAdjuster over all running property claims"
    local response job_id
    response=$(curl -s -X POST "${CLAIMS_URL}/ai-adjuster:enable-batch")
    job_id=$(json_field jobId "${response}")
    if [[ -z "${job_id}" ]]; then
        echo "Error: batch signal failed. Response: ${response}" >&2
        exit 1
    fi
    echo "    started batch job ${job_id} (inspect: temporal batch describe --job-id ${job_id})"

    echo "==> Waiting for the AI adjusters to drain the queue..."
    local deadline pending
    deadline=$(( $(date +%s) + POLL_TIMEOUT ))
    while :; do
        pending=$(count_pending)
        echo "    ${pending} claim(s) still pending"
        if [[ "${pending}" -eq 0 ]]; then
            echo "==> Queue drained: no property claims remain on a human adjuster."
            return 0
        fi
        if [[ $(date +%s) -ge ${deadline} ]]; then
            echo "Error: timed out after ${POLL_TIMEOUT}s with ${pending} claim(s) still pending." >&2
            exit 1
        fi
        sleep 3
    done
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        -h|--help) usage; exit 0 ;;
        *) echo "Error: unknown option '$1'." >&2; usage >&2; exit 1 ;;
    esac
    shift
done

demo_drain
