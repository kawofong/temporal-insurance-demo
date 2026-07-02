/* Policyholder portal page */
/* Displays insurance policies and recent claims in retro 16-bit style */

import { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { policyholder, recentClaims } from "../data/mockData";
import AdminPanel from "./AdminPanel";
import {
  POLICY_ENDPOINT,
  formatStatus,
  getPolicyDescriptor,
  getPolicyIcon,
  getPolicySummary,
  isCancelled,
  titleCase,
} from "./policyHelpers";
import "./Portal.css";

function getStatusClass(status) {
  const map = {
    Approved: "approved",
    Paid: "paid",
    "In Review": "in-review",
    Denied: "denied",
  };
  return map[status] || "in-review";
}

function normalizePolicies(response) {
  const groups = ["auto", "property", "commercial"];

  return groups.flatMap((type) => {
    const policies = Array.isArray(response?.[type]) ? response[type] : [];
    return policies.map((policy) => ({ ...policy, policyType: type }));
  });
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
        <span className={`policy-card-status${isCancelled(policy) ? " policy-card-status--cancelled" : ""}`}>
          {formatStatus(policy.status)}
        </span>
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

function Portal() {
  const navigate = useNavigate();
  const [policies, setPolicies] = useState([]);
  const [isLoadingPolicies, setIsLoadingPolicies] = useState(true);
  const [policyError, setPolicyError] = useState("");

  useEffect(() => {
    const controller = new AbortController();

    async function loadPolicies() {
      try {
        // Scope the dashboard to the signed-in policyholder via their user id.
        const url = `${POLICY_ENDPOINT}?policyHolderId=${encodeURIComponent(policyholder.memberId)}`;
        const response = await fetch(url, { signal: controller.signal });
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

  const openPolicy = (policy) => {
    navigate(`/portal/policies/${policy.policyType}/${policy.policyId}`);
  };

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
        <button className="portal-logout" type="button" onClick={handleLogout}>
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
              <PolicyCard key={`${policy.policyType}-${policy.policyId}`} policy={policy} onSelect={openPolicy} />
            ))}
          </div>
        ) : (
          <div className="policies-placeholder">You have no active insurance policy.</div>
        )}
      </section>

      <section className="claims-section">
        <div className="section-header">
          <h2 className="section-title">Recent Claims</h2>
          <button className="claim-button" type="button" disabled title="Coming soon">
            Start a new claim
          </button>
        </div>
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

      <footer className="portal-footer">Ziggy Insurance ★ Policyholder Portal ★ v1.0</footer>

      <AdminPanel />
    </div>
  );
}

export default Portal;
