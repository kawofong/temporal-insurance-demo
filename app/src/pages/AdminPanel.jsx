/* Adjuster admin simulator */
/* Floating action button that opens a large left-tabbed modal to simulate the
   field adjuster and claims adjuster applications acting on claims. */

import { useEffect, useState } from "react";
import {
  SIGNAL_REFRESH_DELAY_MS,
  approveClaim,
  formatCurrency,
  listClaims,
  submitDamageAssessment,
} from "./claimHelpers";
import "./Portal.css";
import "./AdminPanel.css";

const TABS = [
  { id: "field-adjuster", label: "Field Adjuster", icon: "🔧" },
  { id: "adjuster", label: "Claims Adjuster", icon: "✅" },
];

const DEMO_ADJUSTER_ID = "ADJ-SARAH";

function useClaimQueue(status) {
  const [claims, setClaims] = useState([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState("");
  const [refreshToken, setRefreshToken] = useState(0);

  useEffect(() => {
    const controller = new AbortController();

    async function loadQueue() {
      setIsLoading(true);
      try {
        const results = await listClaims({ status }, { signal: controller.signal });
        setClaims(results);
        setError("");
      } catch (queueError) {
        if (queueError.name !== "AbortError") {
          setError(queueError.message || "Unable to load the queue.");
        }
      } finally {
        if (!controller.signal.aborted) setIsLoading(false);
      }
    }

    loadQueue();
    return () => controller.abort();
  }, [status, refreshToken]);

  function refresh() {
    setRefreshToken((token) => token + 1);
  }

  return { claims, isLoading, error, refresh };
}

// Shared loading/error/empty/list rendering for both adjuster queues.
function ClaimQueueList({ isLoading, error, claims, renderItem }) {
  if (isLoading) return <div className="admin-placeholder">Loading queue...</div>;
  if (error) return <div className="admin-placeholder">{error}</div>;
  if (claims.length === 0) return <div className="admin-placeholder">No claims waiting.</div>;

  return (
    <ul className="admin-queue">
      {claims.map((claim) => (
        <li className="admin-queue-item" key={claim.claimId}>
          {renderItem(claim)}
        </li>
      ))}
    </ul>
  );
}

function FieldAdjusterPanel() {
  const { claims, isLoading, error, refresh } = useClaimQueue("PENDING_DAMAGE_ASSESSMENT");
  const [selectedClaimId, setSelectedClaimId] = useState(null);
  const [summary, setSummary] = useState("");
  const [estimatedCost, setEstimatedCost] = useState("");
  const [isBusy, setIsBusy] = useState(false);
  const [notice, setNotice] = useState("");
  const [formError, setFormError] = useState("");

  function selectClaim(claim) {
    setSelectedClaimId(claim.claimId);
    setSummary("");
    setEstimatedCost("");
    setNotice("");
    setFormError("");
  }

  async function submitAssessment(event) {
    event.preventDefault();
    setIsBusy(true);
    setFormError("");
    try {
      await submitDamageAssessment(selectedClaimId, {
        summary,
        estimatedCost: Number(estimatedCost) || 0,
      });
      setNotice(`Damage assessment submitted for ${selectedClaimId}.`);
      setSelectedClaimId(null);
      window.setTimeout(refresh, SIGNAL_REFRESH_DELAY_MS);
    } catch (submitError) {
      setFormError(submitError.message || "Unable to submit damage assessment.");
    } finally {
      setIsBusy(false);
    }
  }

  return (
    <div className="admin-tab-panel">
      <h3>🔧 Field Adjuster</h3>
      <p>
        Stand-in for the field adjuster application. Here an adjuster dispatched to the
        scene looks up a claim and submits their damage assessment back to the workflow.
      </p>

      {notice && <div className="policy-modal-notice">{notice}</div>}

      <ClaimQueueList
        isLoading={isLoading}
        error={error}
        claims={claims}
        renderItem={(claim) => (
          <>
            <div className="admin-queue-item-header">
              <strong>{claim.claimId}</strong>
              <button type="button" onClick={() => selectClaim(claim)}>
                Assess Damage
              </button>
            </div>
            <p>{claim.incidentDescription}</p>
            <div className="admin-queue-item-meta">
              <span>
                {claim.vehicleYear} {claim.vehicleMake} {claim.vehicleModel}
              </span>
              <span>{claim.incidentLocation}</span>
            </div>
          </>
        )}
      />

      {selectedClaimId && (
        <form className="policy-action-form" onSubmit={submitAssessment}>
          <h4>Damage Assessment — {selectedClaimId}</h4>
          <label>
            Summary
            <textarea value={summary} onChange={(event) => setSummary(event.target.value)} required />
          </label>
          <label>
            Estimated Cost
            <input
              type="number"
              min="0"
              value={estimatedCost}
              onChange={(event) => setEstimatedCost(event.target.value)}
              required
            />
          </label>
          {formError && <div className="policy-modal-notice policy-modal-notice--error">{formError}</div>}
          <div className="policy-form-actions">
            <button className="policy-form-keep" type="button" onClick={() => setSelectedClaimId(null)} disabled={isBusy}>
              Cancel
            </button>
            <button type="submit" disabled={isBusy}>
              {isBusy ? "Submitting..." : "Submit Assessment"}
            </button>
          </div>
        </form>
      )}
    </div>
  );
}

function AdjusterPanel() {
  const { claims, isLoading, error, refresh } = useClaimQueue("PENDING_APPROVAL");
  const [selectedClaim, setSelectedClaim] = useState(null);
  const [approvedPayoutAmount, setApprovedPayoutAmount] = useState("");
  const [notes, setNotes] = useState("");
  const [adjusterId, setAdjusterId] = useState(DEMO_ADJUSTER_ID);
  const [isBusy, setIsBusy] = useState(false);
  const [notice, setNotice] = useState("");
  const [formError, setFormError] = useState("");

  function selectClaim(claim) {
    setSelectedClaim(claim);
    // Nullish coalescing (not ||) preserves a legitimate $0 estimated repair cost.
    setApprovedPayoutAmount(claim.estimatedRepairCost ?? "");
    setNotes("");
    setAdjusterId(DEMO_ADJUSTER_ID);
    setNotice("");
    setFormError("");
  }

  async function submitApproval(event) {
    event.preventDefault();
    setIsBusy(true);
    setFormError("");
    try {
      await approveClaim(selectedClaim.claimId, {
        adjusterId,
        approvedPayoutAmount: Number(approvedPayoutAmount) || 0,
        notes,
      });
      setNotice(`Approval submitted for ${selectedClaim.claimId}.`);
      setSelectedClaim(null);
      window.setTimeout(refresh, SIGNAL_REFRESH_DELAY_MS);
    } catch (submitError) {
      setFormError(submitError.message || "Unable to submit approval.");
    } finally {
      setIsBusy(false);
    }
  }

  return (
    <div className="admin-tab-panel">
      <h3>✅ Claims Adjuster</h3>
      <p>
        Stand-in for the claims adjuster console. Here an adjuster reviews an assessed
        claim and approves the payout to move it toward closure.
      </p>

      {notice && <div className="policy-modal-notice">{notice}</div>}

      <ClaimQueueList
        isLoading={isLoading}
        error={error}
        claims={claims}
        renderItem={(claim) => (
          <>
            <div className="admin-queue-item-header">
              <strong>{claim.claimId}</strong>
              <button type="button" onClick={() => selectClaim(claim)}>
                Review
              </button>
            </div>
            <p>{claim.damageAssessment}</p>
            <div className="admin-queue-item-meta">
              <span>Estimated: {formatCurrency(claim.estimatedRepairCost)}</span>
            </div>
          </>
        )}
      />

      {selectedClaim && (
        <form className="policy-action-form" onSubmit={submitApproval}>
          <h4>Approve Payout — {selectedClaim.claimId}</h4>
          <label>
            Approved Payout Amount
            <input
              type="number"
              min="0"
              value={approvedPayoutAmount}
              onChange={(event) => setApprovedPayoutAmount(event.target.value)}
              required
            />
          </label>
          <label>
            Notes
            <textarea value={notes} onChange={(event) => setNotes(event.target.value)} />
          </label>
          <label>
            Adjuster ID
            <input type="text" value={adjusterId} onChange={(event) => setAdjusterId(event.target.value)} required />
          </label>
          {formError && <div className="policy-modal-notice policy-modal-notice--error">{formError}</div>}
          <div className="policy-form-actions">
            <button className="policy-form-keep" type="button" onClick={() => setSelectedClaim(null)} disabled={isBusy}>
              Cancel
            </button>
            <button type="submit" disabled={isBusy}>
              {isBusy ? "Submitting..." : "Approve Payout"}
            </button>
          </div>
        </form>
      )}
    </div>
  );
}

function AdminPanel() {
  const [isOpen, setIsOpen] = useState(false);
  const [activeTab, setActiveTab] = useState("field-adjuster");

  return (
    <>
      <button
        className="admin-fab"
        type="button"
        onClick={() => setIsOpen(true)}
        aria-label="Open adjuster admin panel"
        title="Adjuster admin panel"
      >
        🛠️
      </button>

      {isOpen && (
        <div className="admin-modal-backdrop" role="presentation" onClick={() => setIsOpen(false)}>
          <section
            className="admin-modal"
            role="dialog"
            aria-modal="true"
            aria-labelledby="admin-modal-title"
            onClick={(event) => event.stopPropagation()}
          >
            <div className="admin-modal-header">
              <h2 id="admin-modal-title">🛠️ Adjuster Admin Panel</h2>
              <button
                className="admin-modal-close"
                type="button"
                onClick={() => setIsOpen(false)}
                aria-label="Close admin panel"
              >
                ×
              </button>
            </div>

            <div className="admin-modal-body">
              <nav className="admin-tabs" aria-label="Adjuster interfaces">
                {TABS.map((tab) => (
                  <button
                    key={tab.id}
                    type="button"
                    className={`admin-tab${activeTab === tab.id ? " admin-tab--active" : ""}`}
                    onClick={() => setActiveTab(tab.id)}
                    aria-pressed={activeTab === tab.id}
                  >
                    <span className="admin-tab-icon">{tab.icon}</span>
                    {tab.label}
                  </button>
                ))}
              </nav>

              <div className="admin-tab-content">
                {activeTab === "field-adjuster" ? <FieldAdjusterPanel /> : <AdjusterPanel />}
              </div>
            </div>
          </section>
        </div>
      )}
    </>
  );
}

export default AdminPanel;
