/* Policyholder portal page */
/* Displays insurance policies and recent claims in retro 16-bit style */

import { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { policyholder } from "../data/mockData";
import AdminPanel from "./AdminPanel";
import {
  claimStatusClass,
  formatClaimStatus,
  formatCurrency,
  formatDate,
  listClaims,
} from "./claimHelpers";
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

function claimAmount(claim) {
  if (claim.approvedPayoutAmount) return formatCurrency(claim.approvedPayoutAmount);
  if (claim.estimatedRepairCost) return formatCurrency(claim.estimatedRepairCost);
  return "—";
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

  const [claims, setClaims] = useState([]);
  const [isLoadingClaims, setIsLoadingClaims] = useState(true);
  const [claimsError, setClaimsError] = useState("");

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

  useEffect(() => {
    const controller = new AbortController();

    async function loadClaims() {
      try {
        const results = await listClaims({ policyHolderId: policyholder.memberId }, { signal: controller.signal });
        setClaims([...results].sort((a, b) => (b.incidentDate || 0) - (a.incidentDate || 0)));
        setClaimsError("");
      } catch (error) {
        if (error.name !== "AbortError") {
          setClaimsError("Unable to load claims right now.");
        }
      } finally {
        if (!controller.signal.aborted) setIsLoadingClaims(false);
      }
    }

    loadClaims();
    return () => controller.abort();
  }, []);

  const hasPolicies = useMemo(() => policies.length > 0, [policies]);
  const hasClaims = useMemo(() => claims.length > 0, [claims]);

  const openClaim = (claim) => {
    navigate(`/portal/claims/${claim.claimId}`);
  };

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
        </div>
        {isLoadingClaims ? (
          <div className="policies-placeholder">Loading claims...</div>
        ) : claimsError ? (
          <div className="policies-placeholder policies-placeholder--error">{claimsError}</div>
        ) : hasClaims ? (
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
                {claims.map((claim) => (
                  <tr key={claim.claimId} className="claims-row-link" onClick={() => openClaim(claim)}>
                    <td>{claim.claimId}</td>
                    <td>Auto</td>
                    <td>{formatDate(claim.incidentDate)}</td>
                    <td>{claim.incidentDescription}</td>
                    <td>{claimAmount(claim)}</td>
                    <td>
                      <span className={`claim-status claim-status--${claimStatusClass(claim.status)}`}>
                        {formatClaimStatus(claim.status)}
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <div className="policies-placeholder">You have no claims yet.</div>
        )}
      </section>

      <footer className="portal-footer">Ziggy Insurance ★ Policyholder Portal ★ v1.0</footer>

      <AdminPanel />
    </div>
  );
}

export default Portal;
