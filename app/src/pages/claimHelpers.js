/* Shared claim presentation and data helpers */
/* Used by the dashboard (Portal), claim details, and the adjuster admin panel */

import { formatDate, readApiError } from "./policyHelpers";

export const CLAIM_ENDPOINT = "/api/v1/claims/auto";
export const SIGNAL_REFRESH_DELAY_MS = 900;

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

export async function fetchClaim(claimId, options) {
  const response = await fetch(`${CLAIM_ENDPOINT}/${claimId}`, options);
  if (!response.ok) {
    throw new Error(await readApiError(response, `Claim API returned ${response.status}`));
  }
  return response.json();
}

export async function listClaims({ policyHolderId, policyId, status } = {}, options) {
  const params = new URLSearchParams();
  if (policyHolderId) params.set("policyHolderId", policyHolderId);
  if (policyId) params.set("policyId", policyId);
  if (status) params.set("status", status);

  const query = params.toString();
  const response = await fetch(`${CLAIM_ENDPOINT}${query ? `?${query}` : ""}`, options);
  if (!response.ok) {
    throw new Error(await readApiError(response, `Claim API returned ${response.status}`));
  }
  const data = await response.json();
  return data.claims || [];
}

export async function submitDamageAssessment(claimId, { summary, estimatedCost }) {
  const response = await fetch(`${CLAIM_ENDPOINT}/${claimId}/damage-assessment`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ summary, estimatedCost }),
  });
  if (!response.ok) {
    throw new Error(await readApiError(response, `Damage assessment returned ${response.status}`));
  }
}

export async function approveClaim(claimId, { adjusterId, approvedPayoutAmount, notes }) {
  const response = await fetch(`${CLAIM_ENDPOINT}/${claimId}/approve`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ adjusterId, approvedPayoutAmount, notes }),
  });
  if (!response.ok) {
    throw new Error(await readApiError(response, `Approval returned ${response.status}`));
  }
}
