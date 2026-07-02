/* Shared policy presentation and data helpers */
/* Used by the dashboard (Portal) and the policy details page (PolicyDetails) */

export const POLICY_ENDPOINT = "/api/v1/policies";
export const SIGNAL_REFRESH_DELAY_MS = 900;

// Per-type configuration for the single "add line item" action a policy supports.
export const ADD_ACTIONS = {
  auto: {
    label: "Add Driver",
    endpoint: "drivers",
    kind: "signal",
    initialValues: { name: "", licenseNumber: "" },
    fields: [
      { name: "name", label: "Name" },
      { name: "licenseNumber", label: "License Number" },
    ],
    buildPayload: (values) => values,
    successMessage: "Driver add submitted. Refreshing policy...",
  },
  property: {
    label: "Add Loss Payee",
    endpoint: "loss-payees",
    kind: "update",
    initialValues: { name: "", loanNumber: "" },
    fields: [
      { name: "name", label: "Name" },
      { name: "loanNumber", label: "Loan Number" },
    ],
    buildPayload: (values) => values,
    successLabel: "Loss payee count",
  },
  commercial: {
    label: "Add Additional Insured",
    endpoint: "additional-insureds",
    kind: "update",
    initialValues: { name: "", relationship: "" },
    fields: [
      { name: "name", label: "Name" },
      { name: "relationship", label: "Relationship" },
    ],
    buildPayload: (values) => values,
    successLabel: "Additional insured count",
  },
};

export function getPolicyIcon(type) {
  const icons = {
    auto: "🚗",
    property: "🏠",
    commercial: "🏢",
  };
  return icons[type] || "📋";
}

export function titleCase(value) {
  if (!value) return "Policy";
  return String(value)
    .replace(/[_-]/g, " ")
    .replace(/\w\S*/g, (word) => word.charAt(0).toUpperCase() + word.slice(1).toLowerCase());
}

export function formatStatus(status) {
  return titleCase(status || "Unknown");
}

export function isCancelled(policy) {
  return String(policy?.status || "").toUpperCase() === "CANCELLED";
}

export function canAddToPolicy(policy) {
  const status = String(policy?.status || "").toUpperCase();
  return status === "ACTIVE" || status === "RENEWAL_PENDING";
}

export function formatDate(value) {
  if (value === null || value === undefined || value === "") return "Not provided";

  const numericValue = Number(value);
  const date = Number.isFinite(numericValue)
    ? new Date(numericValue < 10000000000 ? numericValue * 1000 : numericValue)
    : new Date(value);

  if (Number.isNaN(date.getTime())) return String(value);

  return new Intl.DateTimeFormat("en-US", {
    year: "numeric",
    month: "short",
    day: "numeric",
  }).format(date);
}

export function formatFieldLabel(value) {
  return titleCase(String(value).replace(/([a-z0-9])([A-Z])/g, "$1 $2"));
}

// Internal entity identifiers that are not meaningful to policyholders.
export const HIDDEN_ENTITY_ID_FIELDS = new Set([
  "vehicleId",
  "driverId",
  "propertyId",
  "lossPayeeId",
  "additionalInsuredId",
]);

export function formatFieldValue(value) {
  if (value === null || value === undefined || value === "") return "—";
  if (Array.isArray(value)) return `${value.length} item${value.length === 1 ? "" : "s"}`;
  if (typeof value === "object") {
    return (
      Object.entries(value)
        .filter(([key]) => !HIDDEN_ENTITY_ID_FIELDS.has(key))
        .map(([, entryValue]) => entryValue)
        .filter(Boolean)
        .join(" ") || "—"
    );
  }
  return String(value);
}

export function getPolicySummary(policy) {
  return [
    { label: "Status", value: formatStatus(policy.status) },
    { label: "Policy ID", value: policy.policyId || "Not provided" },
    { label: "Effective", value: formatDate(policy.effectiveDate) },
    { label: "Expiry", value: formatDate(policy.expiryDate) },
  ];
}

export function getPolicyDescriptor(policy) {
  if (policy.policyType === "auto") {
    const vehicle = policy.insuredVehicles?.[0];
    return vehicle ? `${vehicle.year} ${vehicle.make} ${vehicle.model}` : "Auto coverage";
  }

  if (policy.policyType === "property") {
    const property = policy.property;
    return property ? formatFieldValue(property) : "Property coverage";
  }

  if (policy.policyType === "commercial") {
    return policy.businessName || "Commercial coverage";
  }

  return "Insurance policy";
}

export async function readApiError(response, fallback) {
  try {
    const body = await response.json();
    return body.message || body.error || fallback;
  } catch {
    return fallback;
  }
}
