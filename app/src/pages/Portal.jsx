/* Policyholder portal page */
/* Displays insurance policies and recent claims in retro 16-bit style */

import { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { policyholder, recentClaims } from "../data/mockData";
import "./Portal.css";

const POLICY_ENDPOINT = "/api/v1/policies";

function getStatusClass(status) {
  const map = {
    Approved: "approved",
    Paid: "paid",
    "In Review": "in-review",
    Denied: "denied",
  };
  return map[status] || "in-review";
}

function getPolicyIcon(type) {
  const icons = {
    auto: "🚗",
    property: "🏠",
    commercial: "🏢",
  };
  return icons[type] || "📋";
}

function titleCase(value) {
  if (!value) return "Policy";
  return String(value)
    .replace(/[_-]/g, " ")
    .replace(/\w\S*/g, (word) => word.charAt(0).toUpperCase() + word.slice(1).toLowerCase());
}

function formatStatus(status) {
  return titleCase(status || "Unknown");
}

function formatDate(value) {
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

function formatFieldLabel(value) {
  return titleCase(String(value).replace(/([a-z0-9])([A-Z])/g, "$1 $2"));
}

function formatFieldValue(value) {
  if (value === null || value === undefined || value === "") return "—";
  if (Array.isArray(value)) return `${value.length} item${value.length === 1 ? "" : "s"}`;
  if (typeof value === "object") return Object.values(value).filter(Boolean).join(" ") || "—";
  return String(value);
}

function normalizePolicies(response) {
  const groups = ["auto", "property", "commercial"];

  return groups.flatMap((type) => {
    const policies = Array.isArray(response?.[type]) ? response[type] : [];
    return policies.map((policy) => ({ ...policy, policyType: type }));
  });
}

function getPolicySummary(policy) {
  return [
    { label: "Status", value: formatStatus(policy.status) },
    { label: "Policy ID", value: policy.policyId || "Not provided" },
    { label: "Effective", value: formatDate(policy.effectiveDate) },
    { label: "Expiry", value: formatDate(policy.expiryDate) },
  ];
}

function getPolicyDescriptor(policy) {
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

function DetailRows({ data, exclude = [] }) {
  const rows = Object.entries(data || {}).filter(([key]) => !exclude.includes(key));

  return rows.map(([key, value]) => (
    <div className="modal-detail" key={key}>
      <span className="modal-detail-label">{formatFieldLabel(key)}</span>
      {Array.isArray(value) ? (
        <div className="modal-detail-list">
          {value.length === 0 ? (
            <span>None</span>
          ) : (
            value.map((item, index) => <span key={`${key}-${index}`}>{formatFieldValue(item)}</span>)
          )}
        </div>
      ) : (
        <span className="modal-detail-value">{formatFieldValue(value)}</span>
      )}
    </div>
  ));
}

function PolicyCard({ policy, onSelect }) {
  const summary = getPolicySummary(policy);

  return (
    <button className="policy-card" type="button" onClick={() => onSelect(policy)}>
      <div className="policy-card-header">
        <div className="policy-card-type">
          <span className="policy-card-icon">{getPolicyIcon(policy.policyType)}</span>
          <div>
            <div className="policy-card-label">{titleCase(policy.policyType)}</div>
            <div className="policy-card-id">{policy.policyId}</div>
          </div>
        </div>
        <span className="policy-card-status">{formatStatus(policy.status)}</span>
      </div>
      <div className="policy-card-details">
        <div className="policy-detail policy-detail--descriptor">
          <span className="policy-detail-value">{getPolicyDescriptor(policy)}</span>
        </div>
        {summary.map((d) => (
          <div className="policy-detail" key={d.label}>
            <span className="policy-detail-label">{d.label}</span>
            <span className="policy-detail-value">{d.value}</span>
          </div>
        ))}
      </div>
    </button>
  );
}

function PolicyModal({ policy, onClose }) {
  if (!policy) return null;

  return (
    <div className="policy-modal-backdrop" role="presentation" onClick={onClose}>
      <section
        className="policy-modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="policy-modal-title"
        onClick={(event) => event.stopPropagation()}
      >
        <div className="policy-modal-header">
          <div>
            <h3 id="policy-modal-title">
              {getPolicyIcon(policy.policyType)} {titleCase(policy.policyType)} Policy
            </h3>
            <p>{policy.policyId}</p>
          </div>
          <button className="policy-modal-close" type="button" onClick={onClose} aria-label="Close policy details">
            ×
          </button>
        </div>

        <div className="policy-modal-summary">
          {getPolicySummary(policy).map((item) => (
            <div className="modal-summary-tile" key={item.label}>
              <span>{item.label}</span>
              <strong>{item.value}</strong>
            </div>
          ))}
        </div>

        <div className="policy-modal-details">
          <DetailRows data={policy} exclude={["policyType"]} />
        </div>

        <div className="policy-modal-actions">
          <button type="button">Make Payment</button>
          <button type="button">Update Policy</button>
          <button type="button">Start Claim</button>
          <button type="button">Cancel Policy</button>
        </div>
      </section>
    </div>
  );
}

function Portal() {
  const navigate = useNavigate();
  const [policies, setPolicies] = useState([]);
  const [isLoadingPolicies, setIsLoadingPolicies] = useState(true);
  const [policyError, setPolicyError] = useState("");
  const [selectedPolicy, setSelectedPolicy] = useState(null);

  useEffect(() => {
    const controller = new AbortController();

    async function loadPolicies() {
      try {
        const response = await fetch(POLICY_ENDPOINT, { signal: controller.signal });
        if (!response.ok) throw new Error(`Policy API returned ${response.status}`);
        const data = await response.json();
        setPolicies(normalizePolicies(data));
        setPolicyError("");
      } catch (error) {
        if (error.name !== "AbortError") {
          setPolicyError("Unable to load policies right now.");
        }
      } finally {
        if (!controller.signal.aborted) setIsLoadingPolicies(false);
      }
    }

    loadPolicies();
    return () => controller.abort();
  }, []);

  const hasPolicies = useMemo(() => policies.length > 0, [policies]);

  const handleLogout = () => {
    navigate("/login");
  };

  return (
    <div className="portal-page">
      <header className="portal-header">
        <div className="portal-header-left">
          <h1>Welcome, {policyholder.name}</h1>
          <p>Member ID: {policyholder.memberId}</p>
        </div>
        <button className="portal-logout" onClick={handleLogout}>
          Logout
        </button>
      </header>

      <section>
        <h2 className="section-title">My Policies</h2>
        {isLoadingPolicies ? (
          <div className="policies-placeholder">Loading policies...</div>
        ) : policyError ? (
          <div className="policies-placeholder policies-placeholder--error">{policyError}</div>
        ) : hasPolicies ? (
          <div className="policies-grid">
            {policies.map((policy) => (
              <PolicyCard key={`${policy.policyType}-${policy.policyId}`} policy={policy} onSelect={setSelectedPolicy} />
            ))}
          </div>
        ) : (
          <div className="policies-placeholder">You have no active insurance policy.</div>
        )}
      </section>

      <section className="claims-section">
        <h2 className="section-title">Recent Claims</h2>
        <div className="claims-table-wrapper">
          <table className="claims-table">
            <thead>
              <tr>
                <th>Claim ID</th>
                <th>Type</th>
                <th>Date</th>
                <th>Description</th>
                <th>Amount</th>
                <th>Status</th>
              </tr>
            </thead>
            <tbody>
              {recentClaims.map((claim) => (
                <tr key={claim.id}>
                  <td>{claim.id}</td>
                  <td>{claim.type}</td>
                  <td>{claim.date}</td>
                  <td>{claim.description}</td>
                  <td>{claim.amount}</td>
                  <td>
                    <span className={`claim-status claim-status--${getStatusClass(claim.status)}`}>
                      {claim.status}
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>

      <PolicyModal policy={selectedPolicy} onClose={() => setSelectedPolicy(null)} />

      <footer className="portal-footer">Ziggy Insurance ★ Policyholder Portal ★ v1.0</footer>
    </div>
  );
}

export default Portal;
