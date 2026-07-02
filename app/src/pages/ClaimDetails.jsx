/* Claim details page */
/* Full-page view of a single auto claim with a lifecycle status tracker */

import { useCallback, useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import AdminPanel from "./AdminPanel";
import {
  CLAIM_LIFECYCLE_STEPS,
  claimStatusClass,
  fetchClaim,
  formatClaimStatus,
  formatCurrency,
  formatDate,
} from "./claimHelpers";
import "./Portal.css";
import "./PolicyDetails.css";
import "./ClaimDetails.css";

function hasValue(value) {
  return value !== null && value !== undefined && value !== "" && value !== 0;
}

function DetailSection({ title, rows }) {
  const visibleRows = rows.filter((row) => hasValue(row.value));
  if (visibleRows.length === 0) return null;

  return (
    <section>
      <h2 className="section-title">{title}</h2>
      <div className="policy-details-panel">
        {visibleRows.map((row) => (
          <div className="modal-detail" key={row.label}>
            <span className="modal-detail-label">{row.label}</span>
            <span className="modal-detail-value">{row.value}</span>
          </div>
        ))}
      </div>
    </section>
  );
}

function StatusTracker({ claim }) {
  if (claim.status === "REJECTED") {
    return (
      <div className="claim-tracker claim-tracker--rejected">
        <div className="claim-tracker-rejected-label">Claim Rejected</div>
        <p>{claim.rejectionReason || "Coverage could not be verified for this claim."}</p>
      </div>
    );
  }

  const currentIndex = CLAIM_LIFECYCLE_STEPS.findIndex((step) => step.status === claim.status);

  return (
    <div className="claim-tracker">
      {CLAIM_LIFECYCLE_STEPS.map((step, index) => {
        const state = index < currentIndex ? "done" : index === currentIndex ? "current" : "pending";
        return (
          <div className={`claim-tracker-step claim-tracker-step--${state}`} key={step.status}>
            <span className="claim-tracker-dot" />
            <span className="claim-tracker-label">{step.label}</span>
          </div>
        );
      })}
    </div>
  );
}

function ClaimDetails() {
  const { claimId } = useParams();
  const navigate = useNavigate();

  const [claim, setClaim] = useState(null);
  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState("");

  const loadClaim = useCallback(
    async (signal) => {
      setIsLoading(true);
      try {
        const data = await fetchClaim(claimId, signal ? { signal } : undefined);
        setClaim(data);
        setLoadError("");
      } catch (error) {
        if (error.name !== "AbortError") {
          setLoadError(error.message || "Unable to load this claim.");
        }
      } finally {
        if (!signal || !signal.aborted) setIsLoading(false);
      }
    },
    [claimId],
  );

  useEffect(() => {
    const controller = new AbortController();
    loadClaim(controller.signal);
    return () => controller.abort();
  }, [loadClaim]);

  return (
    <div className="portal-page">
      <nav className="details-back">
        <button className="back-link" type="button" onClick={() => navigate("/portal")}>
          ← Back to dashboard
        </button>
      </nav>

      <header className="portal-header">
        <div className="portal-header-left">
          <h1>🚗 Auto Claim</h1>
          <p>{claimId}</p>
        </div>
        {claim && (
          <div className="portal-header-actions">
            <span className={`claim-status claim-status--${claimStatusClass(claim.status)}`}>
              {formatClaimStatus(claim.status)}
            </span>
          </div>
        )}
      </header>

      {isLoading ? (
        <div className="policies-placeholder">Loading claim...</div>
      ) : loadError ? (
        <div className="policies-placeholder policies-placeholder--error">{loadError}</div>
      ) : (
        <>
          <section>
            <h2 className="section-title">Status</h2>
            <StatusTracker claim={claim} />
          </section>

          <DetailSection
            title="Incident"
            rows={[
              { label: "Description", value: claim.incidentDescription },
              { label: "Date", value: formatDate(claim.incidentDate) },
              { label: "Location", value: claim.incidentLocation },
            ]}
          />

          <DetailSection
            title="Vehicle"
            rows={[
              {
                label: "Vehicle",
                value: [claim.vehicleYear, claim.vehicleMake, claim.vehicleModel].filter(Boolean).join(" "),
              },
              { label: "VIN", value: claim.vehicleVin },
            ]}
          />

          <DetailSection
            title="Coverage"
            rows={[
              { label: "Coverage Type", value: claim.coverageType },
              // Deductible is set alongside coverageType at coverage verification, so gate on
              // that (rather than the amount itself) to avoid hiding a legitimate $0 deductible.
              { label: "Deductible", value: hasValue(claim.coverageType) ? formatCurrency(claim.deductible) : null },
            ]}
          />

          <DetailSection
            title="Assessment"
            rows={[
              { label: "Assigned Adjuster", value: claim.assignedAdjusterId },
              { label: "Damage Assessment", value: claim.damageAssessment },
              {
                label: "Estimated Repair Cost",
                // Gate on damageAssessment (set together with the estimate) so a legitimate
                // $0 estimate still renders instead of being mistaken for "not yet assessed".
                value: hasValue(claim.damageAssessment) ? formatCurrency(claim.estimatedRepairCost) : null,
              },
            ]}
          />

          <DetailSection
            title="Approval & Payment"
            rows={[
              {
                label: "Approved Payout",
                // Gate on approvedByAdjusterId (set together with the payout) so a legitimate
                // $0 payout still renders instead of being mistaken for "not yet approved".
                value: hasValue(claim.approvedByAdjusterId) ? formatCurrency(claim.approvedPayoutAmount) : null,
              },
              { label: "Approved By", value: claim.approvedByAdjusterId },
              { label: "Approved At", value: claim.approvedAt ? formatDate(claim.approvedAt) : null },
              { label: "Payment Reference", value: claim.paymentReference },
              { label: "Closed At", value: claim.closedAt ? formatDate(claim.closedAt) : null },
            ]}
          />
        </>
      )}

      <footer className="portal-footer">Ziggy Insurance ★ Policyholder Portal ★ v1.0</footer>

      <AdminPanel />
    </div>
  );
}

export default ClaimDetails;
