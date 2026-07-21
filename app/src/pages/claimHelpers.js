/* Shared claim presentation and data helpers */
/* Used by the dashboard (Portal), claim details, and the adjuster admin panel */

import { formatDate, readApiError } from "./policyHelpers";

// Per-type REST roots. Auto and property claims run the same lifecycle behind
// symmetric endpoints, distinguished only by this path segment.
export const CLAIM_ENDPOINTS = { auto: "/api/v1/claims/auto", property: "/api/v1/claims/property" };
// Back-compat default used by the portal FNOL flow and the claim details page.
export const CLAIM_ENDPOINT = CLAIM_ENDPOINTS.auto;
const endpointFor = (claimType = "auto") => CLAIM_ENDPOINTS[claimType] ?? CLAIM_ENDPOINTS.auto;

export const SIGNAL_REFRESH_DELAY_MS = 900;
// How many claims of each type the adjuster queue loads per page.
export const ADJUSTER_QUEUE_PAGE_SIZE = 10;
// How often the claim details page re-queries the workflow so lifecycle progress appears live.
export const CLAIM_POLL_INTERVAL_MS = 3000;

export { readApiError, formatDate };

// Backend ClaimStatus enum -> portal presentation. Bucket separates open (still
// progressing through the lifecycle) from terminal (workflow complete) states.
export const CLAIM_STATUS_MAP = {
  SUBMITTED: { label: "Submitted", cssClass: "in-review", bucket: "open" },
  COVERAGE_VERIFIED: { label: "Coverage Verified", cssClass: "in-review", bucket: "open" },
  PENDING_DAMAGE_ASSESSMENT: {
    label: "Awaiting Damage Assessment",
    cssClass: "in-review",
    bucket: "open",
  },
  PENDING_APPROVAL: { label: "Awaiting Approval", cssClass: "in-review", bucket: "open" },
  PAYMENT_PROCESSING: { label: "Processing Payment", cssClass: "approved", bucket: "open" },
  CLOSED: { label: "Closed / Paid", cssClass: "paid", bucket: "terminal" },
  REJECTED: { label: "Rejected", cssClass: "denied", bucket: "terminal" },
};

// Ordered lifecycle steps for the status tracker (REJECTED is a separate terminal branch).
export const CLAIM_LIFECYCLE_STEPS = [
  { status: "SUBMITTED", label: "Submitted" },
  { status: "COVERAGE_VERIFIED", label: "Coverage Verified" },
  { status: "PENDING_DAMAGE_ASSESSMENT", label: "Damage Assessment" },
  { status: "PENDING_APPROVAL", label: "Approval" },
  { status: "PAYMENT_PROCESSING", label: "Payment" },
  { status: "CLOSED", label: "Closed" },
];

export function formatClaimStatus(status) {
  return CLAIM_STATUS_MAP[status]?.label || status || "Unknown";
}

export function claimStatusClass(status) {
  return CLAIM_STATUS_MAP[status]?.cssClass || "in-review";
}

// True once the workflow has completed (CLOSED or REJECTED); callers use this to stop polling.
export function isTerminalClaimStatus(status) {
  return CLAIM_STATUS_MAP[status]?.bucket === "terminal";
}

export function formatCurrency(value) {
  if (value === null || value === undefined || value === "") return "—";
  const numericValue = Number(value);
  if (!Number.isFinite(numericValue)) return "—";
  return new Intl.NumberFormat("en-US", { style: "currency", currency: "USD", maximumFractionDigits: 0 }).format(
    numericValue,
  );
}

export async function submitFnol(payload) {
  const response = await fetch(CLAIM_ENDPOINT, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
  if (!response.ok) {
    throw new Error(await readApiError(response, `Claim submission returned ${response.status}`));
  }
  return response.json();
}

export async function fetchClaim(claimId, options, claimType = "auto") {
  const response = await fetch(`${endpointFor(claimType)}/${claimId}`, options);
  if (!response.ok) {
    throw new Error(await readApiError(response, `Claim API returned ${response.status}`));
  }
  return response.json();
}

// Fetches a single page of claims and returns both the page and the cursor for the
// next one (null when the visibility stream is exhausted).
export async function listClaimsPage(
  { policyHolderId, policyId, status, pageSize, pageToken } = {},
  options,
  claimType = "auto",
) {
  const params = new URLSearchParams();
  if (policyHolderId) params.set("policyHolderId", policyHolderId);
  if (policyId) params.set("policyId", policyId);
  if (status) params.set("status", status);
  if (pageSize) params.set("pageSize", pageSize);
  if (pageToken) params.set("pageToken", pageToken);

  const query = params.toString();
  const response = await fetch(`${endpointFor(claimType)}${query ? `?${query}` : ""}`, options);
  if (!response.ok) {
    throw new Error(await readApiError(response, `Claim API returned ${response.status}`));
  }
  const data = await response.json();
  return { claims: data.claims || [], nextPageToken: data.nextPageToken || null };
}

export async function listClaims(filters, options, claimType = "auto") {
  const { claims } = await listClaimsPage(filters, options, claimType);
  return claims;
}

// Adjuster-queue loader: fetches one page of auto and one page of property claims in
// the given status in parallel, tags each with its type (for write routing), and
// returns the per-type cursors for "load more". A cursor of null means that type is
// exhausted; pass it back and that source is skipped rather than re-fetched.
export async function listAdjusterQueue(
  status,
  { autoPageToken, propertyPageToken, pageSize = ADJUSTER_QUEUE_PAGE_SIZE } = {},
  options,
) {
  const fetchType = (claimType, pageToken) => {
    if (pageToken === null) return Promise.resolve({ claims: [], nextPageToken: null });
    return listClaimsPage({ status, pageSize, pageToken: pageToken || undefined }, options, claimType);
  };

  const [auto, property] = await Promise.all([
    fetchType("auto", autoPageToken),
    fetchType("property", propertyPageToken),
  ]);

  return {
    claims: [
      ...auto.claims.map((claim) => ({ ...claim, claimType: "auto" })),
      ...property.claims.map((claim) => ({ ...claim, claimType: "property" })),
    ],
    autoPageToken: auto.nextPageToken,
    propertyPageToken: property.nextPageToken,
  };
}

export async function submitDamageAssessment(claimId, { summary, estimatedCost }, claimType = "auto") {
  const response = await fetch(`${endpointFor(claimType)}/${claimId}/damage-assessment`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ summary, estimatedCost }),
  });
  if (!response.ok) {
    throw new Error(await readApiError(response, `Damage assessment returned ${response.status}`));
  }
}

export async function approveClaim(claimId, { adjusterId, approvedPayoutAmount, notes }, claimType = "auto") {
  const response = await fetch(`${endpointFor(claimType)}/${claimId}/approve`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ adjusterId, approvedPayoutAmount, notes }),
  });
  if (!response.ok) {
    throw new Error(await readApiError(response, `Approval returned ${response.status}`));
  }
}

export async function denyClaim(claimId, { adjusterId, reason }, claimType = "auto") {
  const response = await fetch(`${endpointFor(claimType)}/${claimId}/deny`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ adjusterId, reason }),
  });
  if (!response.ok) {
    throw new Error(await readApiError(response, `Denial returned ${response.status}`));
  }
}
