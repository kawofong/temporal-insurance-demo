/* Catastrophe (CAT) event data + presentation helpers */
/* Used by the CAT event tab of the adjuster admin panel. Mirrors claimHelpers.js. */

import { readApiError } from "./policyHelpers";

export const CAT_ENDPOINT = "/api/v1/cat";
// How often the CAT panel re-queries the workflow so progress appears live.
export const CAT_POLL_INTERVAL_MS = 3000;

export { readApiError };

// Backend CATEventLifecycle -> portal presentation (label + bucket). Bucket separates
// open (still fanning out claims) from terminal (workflow complete) states.
export const CAT_LIFECYCLE_MAP = {
  DECLARED: { label: "Declared", bucket: "open" },
  SPAWNING: { label: "Spawning claims", bucket: "open" },
  COMPLETED: { label: "Completed", bucket: "terminal" },
};

// True once the workflow has completed; callers use this to stop polling.
export function isTerminalCatStatus(status) {
  return CAT_LIFECYCLE_MAP[status]?.bucket === "terminal";
}

export function formatCatStatus(status) {
  return CAT_LIFECYCLE_MAP[status]?.label || status || "Unknown";
}

// percentComplete arrives 0..100 (backend computes totalClaimsOpened/totalClaimsExpected*100).
// Clamp/round here so the bar is always a clean integer 0..100.
export function catProgressPercent(status) {
  const raw = Number(status?.percentComplete);
  if (!Number.isFinite(raw)) return 0;
  return Math.min(100, Math.max(0, Math.round(raw)));
}

// Lowercase, replace every run of non-alphanumeric chars with a single "-", trim edges.
export function slugify(text) {
  return String(text ?? "")
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "");
}

// Pure, deterministic id generator: "evt-YYYY-MM-DD-<slug(eventName)>".
// The caller passes the Date explicitly (the panel passes new Date()) so this stays testable.
export function generateCatEventId(eventName, date) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  const slug = slugify(eventName) || "event";
  return `evt-${year}-${month}-${day}-${slug}`;
}

export async function declareCatEvent({ catEventId, eventName, affectedRegion, totalClaimsToGenerate }) {
  const response = await fetch(CAT_ENDPOINT, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    // Send only the four request fields — never batchSize (a workflow constant).
    body: JSON.stringify({ catEventId, eventName, affectedRegion, totalClaimsToGenerate }),
  });
  if (!response.ok) {
    throw new Error(await readApiError(response, `CAT event declaration returned ${response.status}`));
  }
  return response.json();
}

export async function fetchCatEventStatus(catEventId, options) {
  const response = await fetch(`${CAT_ENDPOINT}/${catEventId}`, options);
  if (!response.ok) {
    throw new Error(await readApiError(response, `CAT event API returned ${response.status}`));
  }
  return response.json();
}
