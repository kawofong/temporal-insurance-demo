#!/usr/bin/env bash
# Sets up the demo environment: registers the search attributes and creates the cross-domain
# Nexus endpoints (idempotent) and, if the API is reachable, seeds demo data. Namespace setup uses
# `temporal operator` for a local dev server (--target local) and `temporal cloud` for a Temporal
# Cloud namespace (--target cloud). Safe to re-run.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Config — override via environment variables when targeting a non-local setup.
# NAMESPACE is the namespace the search attributes and endpoints are provisioned on; it defaults
# to the connection namespace (TEMPORAL_NAMESPACE) so Cloud runs target the Cloud namespace.
NAMESPACE="${NEXUS_NAMESPACE:-${TEMPORAL_NAMESPACE:-default}}"
# API key used by the `temporal cloud` control-plane commands (--target cloud).
API_KEY="${TEMPORAL_API_KEY:-}"
API_BASE_URL="${API_BASE_URL:-http://localhost:8080}"
API_CONNECT_TIMEOUT="${API_CONNECT_TIMEOUT:-2}"

# Which command family the namespace-setup phase uses: 'local' -> temporal operator (dev server),
# 'cloud' -> temporal cloud (control plane, authenticated with API_KEY).
TARGET="local"

# Prints usage.
usage() {
    cat <<'EOF'
Usage: demo-setup.sh [--target local|cloud] [--setup-namespace-only | --seed-demo-only]

  --target local          Set up the namespace with `temporal operator` against a local dev
                          server (default).
  --target cloud          Set up the namespace with `temporal cloud` against a Temporal Cloud
                          namespace (uses TEMPORAL_NAMESPACE/TEMPORAL_API_KEY).

  (no phase flag)         Set up the namespace and seed demo data (default).
  --setup-namespace-only  Only set up the namespace: register the search attributes and create
                          the Nexus endpoints.
  --seed-demo-only        Only seed demo data via the API.
  -h, --help              Show this help.
EOF
}

# Checks whether an exact quoted JSON token appears in a list command's output. Matching the
# quoted token finds the name whether the CLI returns it as an object key or a field value,
# without depending on the exact JSON schema (which differs between operator and cloud).
json_has_token() {
    local token="$1" json="$2"
    grep -q "\"${token}\"" <<<"${json}"
}

# Lists existing Nexus endpoints as JSON, for the existence check. Uses `temporal operator`
# locally and `temporal cloud` for Temporal Cloud. `|| true` so a failed list (e.g. server
# unreachable) yields empty output rather than aborting under `set -e`; the create then surfaces
# the real error.
list_nexus_endpoints_json() {
    if [[ "${TARGET}" == "cloud" ]]; then
        temporal cloud nexus endpoint list --api-key "${API_KEY}" -o json 2>/dev/null || true
    else
        temporal operator nexus endpoint list -o json --tls=false 2>/dev/null || true
    fi
}

# Creates one Nexus endpoint (name, target task queue, description file) that routes a
# cross-domain call, skipping it if it is already present in $4 (the endpoint list JSON). Uses
# `temporal operator` locally and `temporal cloud` for Temporal Cloud (where the endpoint must
# also allow the caller namespace).
create_nexus_endpoint() {
    local name="$1" task_queue="$2" description_file="$3" existing_json="$4"
    echo "==> Nexus endpoint '${name}' -> ${NAMESPACE}/${task_queue}"
    if json_has_token "${name}" "${existing_json}"; then
        echo "    already exists — skipping."
        return 0
    fi
    local output
    if [[ "${TARGET}" == "cloud" ]]; then
        # --allow-namespace grants the caller (same namespace, where the policy/claim workflows
        # run) permission to invoke the endpoint.
        # --idempotent is a safety net: the existence check above already skips existing
        # endpoints, but this keeps a re-run from failing if that check ever misses one.
        if output=$(temporal cloud nexus endpoint create \
            --name "${name}" \
            --target-namespace "${NAMESPACE}" \
            --target-task-queue "${task_queue}" \
            --description-file "${description_file}" \
            --allow-namespace "${NAMESPACE}" \
            --idempotent \
            --auto-confirm \
            --api-key "${API_KEY}" 2>&1); then
            echo "    created."
        else
            echo "    failed to create Nexus endpoint:" >&2
            echo "${output}" >&2
            return 1
        fi
    else
        # --tls=false: the local dev server is always plaintext (the CLI otherwise defaults
        # to TLS and fails the handshake).
        if output=$(temporal operator nexus endpoint create \
            --name "${name}" \
            --target-namespace "${NAMESPACE}" \
            --target-task-queue "${task_queue}" \
            --description-file "${description_file}" \
            --tls=false 2>&1); then
            echo "    created."
        else
            echo "    failed to create Nexus endpoint:" >&2
            echo "${output}" >&2
            return 1
        fi
    fi
}

# Creates every Nexus endpoint the demo relies on. Each domain that exposes a Nexus service
# (notifications, payment) gets one endpoint routing callers to its task queue. The endpoint list
# is fetched once and passed to each create so existing endpoints are skipped.
create_nexus_endpoints() {
    local existing_json
    existing_json="$(list_nexus_endpoints_json)"
    create_nexus_endpoint "notifications-ep" "notifications-task-queue" \
        "${SCRIPT_DIR}/nexus-notifications-ep.md" "${existing_json}"
    create_nexus_endpoint "payment-ep" "payment-task-queue" \
        "${SCRIPT_DIR}/nexus-payment-ep.md" "${existing_json}"
}

# The custom Keyword search attributes the workflows upsert. They must be registered on the
# namespace or workflows that upsert them hang. The local dev server registers these via the
# temporal:dev mise task; Cloud namespaces need them created here.
SEARCH_ATTRIBUTES=(policyHolderId policyStatus policyId claimStatus catEventId)

# Lists existing custom search attributes as JSON, for the existence check. Uses `temporal
# operator` locally and `temporal cloud` for Temporal Cloud. `|| true` as above.
list_search_attributes_json() {
    if [[ "${TARGET}" == "cloud" ]]; then
        temporal cloud namespace search-attribute list \
            --namespace "${NAMESPACE}" --api-key "${API_KEY}" -o json 2>/dev/null || true
    else
        temporal operator search-attribute list \
            --namespace "${NAMESPACE}" -o json --tls=false 2>/dev/null || true
    fi
}

# Registers each search attribute, skipping any already present on the namespace. Uses `temporal
# operator` locally and `temporal cloud` for Temporal Cloud. The list is fetched once up front.
register_search_attributes() {
    local sa output existing_json
    existing_json="$(list_search_attributes_json)"
    for sa in "${SEARCH_ATTRIBUTES[@]}"; do
        echo "==> Search attribute '${sa}' (Keyword) on ${NAMESPACE}"
        if json_has_token "${sa}" "${existing_json}"; then
            echo "    already exists — skipping."
            continue
        fi
        if [[ "${TARGET}" == "cloud" ]]; then
            # --idempotent is a safety net alongside the existence check above (see create_nexus_endpoint).
            if output=$(temporal cloud namespace search-attribute create \
                --namespace "${NAMESPACE}" \
                --name "${sa}" \
                --type Keyword \
                --idempotent \
                --api-key "${API_KEY}" 2>&1); then
                echo "    registered."
            else
                echo "    failed to register search attribute:" >&2
                echo "${output}" >&2
                return 1
            fi
        else
            # --tls=false: the local dev server is always plaintext (the CLI otherwise defaults
            # to TLS and fails the handshake).
            if output=$(temporal operator search-attribute create \
                --namespace "${NAMESPACE}" \
                --name "${sa}" \
                --type Keyword \
                --tls=false 2>&1); then
                echo "    registered."
            else
                echo "    failed to register search attribute:" >&2
                echo "${output}" >&2
                return 1
            fi
        fi
    done
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

# Parse the --target flag and the mutually exclusive phase flags.
mode_from_flag=""
while [[ $# -gt 0 ]]; do
    case "$1" in
        --target)
            shift
            [[ $# -gt 0 ]] || { echo "Error: --target requires a value (local|cloud)." >&2; exit 1; }
            case "$1" in
                local|cloud) TARGET="$1" ;;
                *) echo "Error: --target must be 'local' or 'cloud', got '$1'." >&2; exit 1 ;;
            esac
            ;;
        --setup-namespace-only)
            [[ -n "${mode_from_flag}" ]] && { echo "Error: --setup-namespace-only and --seed-demo-only are mutually exclusive." >&2; exit 1; }
            mode_from_flag="namespace-only"
            ;;
        --seed-demo-only)
            [[ -n "${mode_from_flag}" ]] && { echo "Error: --setup-namespace-only and --seed-demo-only are mutually exclusive." >&2; exit 1; }
            mode_from_flag="seed-only"
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        *)
            echo "Error: unknown option '$1'." >&2
            usage >&2
            exit 1
            ;;
    esac
    shift
done

# Resolve which phases run. An explicit flag wins; otherwise SKIP_NEXUS_SETUP=true (set by
# mise.cloud.toml, where the Cloud namespace is pre-provisioned) seeds only; otherwise run both.
if [[ -n "${mode_from_flag}" ]]; then
    MODE="${mode_from_flag}"
elif [[ "${SKIP_NEXUS_SETUP:-false}" == "true" ]]; then
    MODE="seed-only"
else
    MODE="all"
fi

if [[ "${MODE}" == "all" || "${MODE}" == "namespace-only" ]]; then
    # The `temporal cloud` commands need credentials; fail early with a clear message rather than
    # a confusing CLI error if .env.cloud is missing or half-filled.
    if [[ "${TARGET}" == "cloud" ]]; then
        [[ -n "${API_KEY}" ]] || { echo "Error: --target cloud requires TEMPORAL_API_KEY." >&2; exit 1; }
        [[ -n "${NAMESPACE}" ]] || { echo "Error: --target cloud requires TEMPORAL_NAMESPACE." >&2; exit 1; }
    fi
    register_search_attributes
    create_nexus_endpoints
fi
if [[ "${MODE}" == "all" || "${MODE}" == "seed-only" ]]; then
    seed_demo_data
fi
echo "==> Demo environment ready."
