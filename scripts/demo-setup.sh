#!/usr/bin/env bash
# Sets up the demo environment: creates the cross-domain Nexus endpoints (idempotent) and,
# if the API is reachable, triggers the demo data seed endpoint. Safe to re-run.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Config — override via environment variables when targeting a non-local setup.
NEXUS_NAMESPACE="${NEXUS_NAMESPACE:-default}"
API_BASE_URL="${API_BASE_URL:-http://localhost:8080}"
API_CONNECT_TIMEOUT="${API_CONNECT_TIMEOUT:-2}"

# Creates one Nexus endpoint (name, target task queue, description file) that routes a
# cross-domain call. Tolerates an existing endpoint so the task is idempotent, but surfaces any
# other failure (e.g. server unreachable).
create_nexus_endpoint() {
    local name="$1" task_queue="$2" description_file="$3"
    echo "==> Creating Nexus endpoint '${name}' -> ${NEXUS_NAMESPACE}/${task_queue}"
    local output
    if output=$(temporal operator nexus endpoint create \
        --name "${name}" \
        --target-namespace "${NEXUS_NAMESPACE}" \
        --target-task-queue "${task_queue}" \
        --description-file "${description_file}" 2>&1); then
        echo "    created."
    elif grep -qiE "already (exists|registered)" <<<"${output}"; then
        echo "    already exists — skipping."
    else
        echo "    failed to create Nexus endpoint:" >&2
        echo "${output}" >&2
        return 1
    fi
}

# Creates every Nexus endpoint the demo relies on. Each domain that exposes a Nexus service
# (notifications, payment) gets one endpoint routing callers to its task queue.
create_nexus_endpoints() {
    create_nexus_endpoint "notifications-ep" "notifications-task-queue" \
        "${SCRIPT_DIR}/nexus-notifications-ep.md"
    create_nexus_endpoint "payment-ep" "payment-task-queue" \
        "${SCRIPT_DIR}/nexus-payment-ep.md"
}

# Seeds demo data via the API, but only if the API is reachable — the demo can be set up
# before the API is started, so an unreachable API is a skip, not an error.
seed_demo_data() {
    echo "==> Checking API at ${API_BASE_URL}"
    if ! curl -s -o /dev/null --connect-timeout "${API_CONNECT_TIMEOUT}" "${API_BASE_URL}"; then
        echo "    API not reachable — skipping demo data seed."
        echo "    Start it with 'mise run api', then re-run 'mise run demo:setup'."
        return 0
    fi

    echo "    API is up — seeding demo data..."
    local status
    status=$(curl -s -o /dev/null -w "%{http_code}" -X POST "${API_BASE_URL}/api/v1/demo/setup")
    if [[ "${status}" == "200" ]]; then
        echo "    demo data seeded (HTTP ${status})."
    else
        echo "    demo setup request failed (HTTP ${status})." >&2
        return 1
    fi
}

create_nexus_endpoints
seed_demo_data
echo "==> Demo environment ready."
