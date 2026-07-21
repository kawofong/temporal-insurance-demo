#!/usr/bin/env bash
# End-to-end demo driver for the property-claim adjuster paths (spec §8). Drives the REST API
# with curl + status polling. Four modes:
#
#   human     Baseline: a human field adjuster submits the damage assessment and a human claim
#             adjuster approves the payout. No AI involved.
#   ai        Fully autonomous: the claim is opened already in AI mode (aiAdjusterEnabled at
#             intake); the field- and claim-adjuster agents assess and approve with no human input.
#   takeover  Act 4 "human -> AI": a normal claim parks at PENDING_DAMAGE_ASSESSMENT, then a
#             single enableAiAdjuster signal hands the parked claim to the agents.
#   drain     Seed several pending claims, then one batch enableAiAdjuster signal (over a
#             Visibility query) drains them all to CLOSED.
#
# Prerequisites (separate terminals): `mise run temporal:dev`, `mise run temporal:worker`,
# `mise run api`, and for the ai/takeover/drain modes `mise run agents:worker` (needs Ollama).
set -euo pipefail

API_BASE_URL="${API_BASE_URL:-http://localhost:8080}"
CLAIMS_URL="${API_BASE_URL}/api/v1/claims/property"
POLL_TIMEOUT="${POLL_TIMEOUT:-180}"   # seconds to wait for a status (LLM paths are slower)
DRAIN_COUNT="${DRAIN_COUNT:-3}"        # claims seeded by the drain scenario

MODE=""

usage() {
    cat <<'EOF'
Usage: demo-adjuster.sh --mode human|ai|takeover|drain

  --mode human      Human field + claim adjuster (baseline).
  --mode ai         Fully autonomous: AI adjusters, enabled at intake.
  --mode takeover   Park a human claim, then hand it to the AI adjusters via signal.
  --mode drain      Seed several pending claims, then batch-signal them all to AI.
  -h, --help        Show this help.

Env overrides: API_BASE_URL (default http://localhost:8080), POLL_TIMEOUT (seconds),
DRAIN_COUNT (claims seeded by --mode drain).
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

# Opens a property claim. $1 = aiAdjusterEnabled (true|false). Echoes the generated claim id.
open_claim() {
    local ai_enabled="$1"
    local body response claim_id
    body=$(cat <<EOF
{
  "policyId": "demo-property-001",
  "policyHolderId": "PH-001",
  "incidentDescription": "Hurricane-force winds tore off roof shingles and shattered windows.",
  "incidentDate": 1760000000000,
  "propertyAddress": "742 Evergreen Terrace, Springfield",
  "propertyType": "SINGLE_FAMILY",
  "aiAdjusterEnabled": ${ai_enabled}
}
EOF
)
    response=$(curl -s -X POST "${CLAIMS_URL}" -H 'Content-Type: application/json' -d "${body}")
    claim_id=$(json_field claimId "${response}")
    if [[ -z "${claim_id}" ]]; then
        echo "Error: failed to open claim. Response: ${response}" >&2
        exit 1
    fi
    echo "${claim_id}"
}

claim_status() {
    local claim_id="$1"
    json_field status "$(curl -s "${CLAIMS_URL}/${claim_id}")"
}

# Blocks until $1 reaches status $2 (or POLL_TIMEOUT elapses).
wait_for_status() {
    local claim_id="$1" expected="$2" deadline status
    deadline=$(( $(date +%s) + POLL_TIMEOUT ))
    while :; do
        status=$(claim_status "${claim_id}")
        if [[ "${status}" == "${expected}" ]]; then
            echo "    ${claim_id} -> ${status}"
            return 0
        fi
        if [[ $(date +%s) -ge ${deadline} ]]; then
            echo "Error: timed out waiting for ${claim_id} to reach ${expected} (last: ${status})." >&2
            exit 1
        fi
        sleep 2
    done
}

show_outcome() {
    local claim_id="$1"
    echo "==> Final claim state:"
    curl -s "${CLAIMS_URL}/${claim_id}"
    echo
}

demo_human() {
    require_api
    echo "==> [human] Opening a property claim (default human path)"
    local claim_id; claim_id=$(open_claim false)
    echo "    opened ${claim_id}"
    wait_for_status "${claim_id}" PENDING_DAMAGE_ASSESSMENT

    echo "==> [human] Field adjuster submits the damage assessment"
    curl -s -o /dev/null -X POST "${CLAIMS_URL}/${claim_id}/damage-assessment" \
        -H 'Content-Type: application/json' \
        -d '{ "summary": "Roof and window damage with water intrusion.", "estimatedCost": 24500 }'
    wait_for_status "${claim_id}" PENDING_APPROVAL

    echo "==> [human] Claim adjuster approves the payout"
    curl -s -o /dev/null -X POST "${CLAIMS_URL}/${claim_id}/approve" \
        -H 'Content-Type: application/json' \
        -d '{ "adjusterId": "adj-sarah", "approvedPayoutAmount": 23500, "notes": "Approved after review" }'
    wait_for_status "${claim_id}" CLOSED
    show_outcome "${claim_id}"
}

demo_ai() {
    require_api
    echo "==> [ai] Opening a property claim already in AI mode (aiAdjusterEnabled at intake)"
    local claim_id; claim_id=$(open_claim true)
    echo "    opened ${claim_id} — field + claim adjuster agents will assess and approve"
    wait_for_status "${claim_id}" CLOSED
    show_outcome "${claim_id}"
}

demo_takeover() {
    require_api
    echo "==> [takeover] Opening a normal (human) property claim"
    local claim_id; claim_id=$(open_claim false)
    echo "    opened ${claim_id}"
    wait_for_status "${claim_id}" PENDING_DAMAGE_ASSESSMENT

    echo "==> [takeover] Handing the parked claim to the AI adjusters via signal"
    curl -s -o /dev/null -X POST "${CLAIMS_URL}/${claim_id}/ai-adjuster"
    wait_for_status "${claim_id}" CLOSED
    show_outcome "${claim_id}"
}

demo_drain() {
    require_api
    echo "==> [drain] Seeding ${DRAIN_COUNT} normal property claims"
    local ids=() claim_id
    for ((i = 0; i < DRAIN_COUNT; i++)); do
        claim_id=$(open_claim false)
        ids+=("${claim_id}")
        echo "    opened ${claim_id}"
    done
    for claim_id in "${ids[@]}"; do
        wait_for_status "${claim_id}" PENDING_DAMAGE_ASSESSMENT
    done

    echo "==> [drain] Batch-signal enableAiAdjuster over all PENDING_DAMAGE_ASSESSMENT claims"
    curl -s -X POST "${CLAIMS_URL}/ai-adjuster:enable-batch?status=PENDING_DAMAGE_ASSESSMENT"
    echo
    for claim_id in "${ids[@]}"; do
        wait_for_status "${claim_id}" CLOSED
    done
    echo "==> [drain] All ${DRAIN_COUNT} claims drained to CLOSED."
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --mode)
            shift
            [[ $# -gt 0 ]] || { echo "Error: --mode requires a value." >&2; exit 1; }
            MODE="$1"
            ;;
        -h|--help) usage; exit 0 ;;
        *) echo "Error: unknown option '$1'." >&2; usage >&2; exit 1 ;;
    esac
    shift
done

case "${MODE}" in
    human)    demo_human ;;
    ai)       demo_ai ;;
    takeover) demo_takeover ;;
    drain)    demo_drain ;;
    "")       echo "Error: --mode is required." >&2; usage >&2; exit 1 ;;
    *)        echo "Error: unknown mode '${MODE}'." >&2; usage >&2; exit 1 ;;
esac
