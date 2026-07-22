/* Adjuster admin simulator */
/* Floating action button that opens a large left-tabbed modal to simulate the
   field adjuster and claims adjuster applications acting on claims. */

import { useEffect, useState } from "react";
import {
  SIGNAL_REFRESH_DELAY_MS,
  approveClaim,
  denyClaim,
  formatCurrency,
  listAdjusterQueue,
  submitDamageAssessment,
} from "./claimHelpers";
import {
  CAT_POLL_INTERVAL_MS,
  MAX_CLAIMS_PER_EVENT,
  catProgressPercent,
  declareCatEvent,
  fetchCatEventStatus,
  formatCatStatus,
  generateCatEventId,
  isTerminalCatStatus,
} from "./catHelpers";
import "./Portal.css";
import "./AdminPanel.css";

const TABS = [
  { id: "field-adjuster", label: "Field Adjuster", icon: "🔧" },
  { id: "adjuster", label: "Claims Adjuster", icon: "✅" },
  { id: "cat-event", label: "Catastrophe Event", icon: "🌪️" },
];

const DEMO_ADJUSTER_ID = "adj-sarah";

// Prefilled so the operator can declare with one click during a demo (see spec §9).
const CAT_DEMO_DEFAULTS = {
  eventName: "Butte County Wildfire",
  affectedRegion: "Northern California",
  totalClaimsToGenerate: "5",
};

function useClaimQueue(status) {
  const [claims, setClaims] = useState([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isLoadingMore, setIsLoadingMore] = useState(false);
  const [error, setError] = useState("");
  const [refreshToken, setRefreshToken] = useState(0);
  // Per-type cursors: undefined = not loaded yet, a string = more pages remain,
  // null = that type is exhausted.
  const [autoPageToken, setAutoPageToken] = useState(undefined);
  const [propertyPageToken, setPropertyPageToken] = useState(undefined);

  useEffect(() => {
    const controller = new AbortController();

    async function loadQueue() {
      setIsLoading(true);
      try {
        const page = await listAdjusterQueue(status, {}, { signal: controller.signal });
        setClaims(page.claims);
        setAutoPageToken(page.autoPageToken);
        setPropertyPageToken(page.propertyPageToken);
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

  async function loadMore() {
    setIsLoadingMore(true);
    try {
      const page = await listAdjusterQueue(status, { autoPageToken, propertyPageToken });
      setClaims((previous) => [...previous, ...page.claims]);
      setAutoPageToken(page.autoPageToken);
      setPropertyPageToken(page.propertyPageToken);
      setError("");
    } catch (queueError) {
      setError(queueError.message || "Unable to load more claims.");
    } finally {
      setIsLoadingMore(false);
    }
  }

  function refresh() {
    setRefreshToken((token) => token + 1);
  }

  const hasMore = Boolean(autoPageToken) || Boolean(propertyPageToken);

  return { claims, isLoading, isLoadingMore, error, hasMore, loadMore, refresh };
}

// Polls the CAT event workflow so declared-event progress appears live. Cancels the
// interval on unmount, when the id changes, and once the event reaches a terminal state.
// A failed poll is non-fatal: it surfaces an error but keeps the last known status and
// keeps trying (mirrors useClaimQueue's AbortController handling).
function useCatEventProgress(catEventId) {
  const [status, setStatus] = useState(null);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    if (!catEventId) {
      setStatus(null);
      setIsLoading(false);
      setError("");
      return undefined;
    }

    const controller = new AbortController();
    let intervalId;
    setStatus(null);
    setIsLoading(true);

    async function poll() {
      try {
        const next = await fetchCatEventStatus(catEventId, { signal: controller.signal });
        setStatus(next);
        setError("");
        if (isTerminalCatStatus(next.status)) clearInterval(intervalId);
      } catch (pollError) {
        if (pollError.name !== "AbortError") {
          setError(pollError.message || "Unable to load CAT event progress.");
        }
      } finally {
        if (!controller.signal.aborted) setIsLoading(false);
      }
    }

    poll();
    intervalId = setInterval(poll, CAT_POLL_INTERVAL_MS);

    return () => {
      controller.abort();
      clearInterval(intervalId);
    };
  }, [catEventId]);

  return { status, isLoading, error };
}

// Shared loading/error/empty/list rendering for both adjuster queues.
function ClaimQueueList({ isLoading, error, claims, renderItem, hasMore, onLoadMore, isLoadingMore }) {
  if (isLoading) return <div className="admin-placeholder">Loading queue...</div>;
  // Only surface the error as a full-panel placeholder on the initial load. A failed
  // "load more" keeps the already-loaded claims visible with the error shown inline.
  if (error && claims.length === 0) return <div className="admin-placeholder">{error}</div>;
  if (claims.length === 0) return <div className="admin-placeholder">No claims waiting.</div>;

  return (
    <>
      <ul className="admin-queue">
        {claims.map((claim) => (
          <li className="admin-queue-item" key={claim.claimId}>
            {renderItem(claim)}
          </li>
        ))}
      </ul>
      {error && <div className="admin-placeholder">{error}</div>}
      {hasMore && (
        <button type="button" className="admin-load-more" onClick={onLoadMore} disabled={isLoadingMore}>
          {isLoadingMore ? "Loading..." : "Load more"}
        </button>
      )}
    </>
  );
}

// Centered popup that holds a claim action form, stacked above the admin panel so an adjuster
// can act on a claim without scrolling past a long queue. Closes on backdrop click, ×, or Escape.
function ClaimActionModal({ title, onClose, children }) {
  useEffect(() => {
    function onKeyDown(event) {
      if (event.key === "Escape") onClose();
    }
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [onClose]);

  return (
    <div className="admin-action-backdrop" role="presentation" onClick={onClose}>
      <section
        className="admin-action-modal"
        role="dialog"
        aria-modal="true"
        aria-label={title}
        onClick={(event) => event.stopPropagation()}
      >
        <div className="admin-action-header">
          <h4>{title}</h4>
          <button className="admin-modal-close" type="button" onClick={onClose} aria-label="Close form">
            ×
          </button>
        </div>
        {children}
      </section>
    </div>
  );
}

// Badge distinguishing auto from property claims in the shared adjuster queues.
function ClaimTypeBadge({ claimType }) {
  const isProperty = claimType === "property";
  return (
    <span className={`admin-claim-type-badge admin-claim-type-badge--${isProperty ? "property" : "auto"}`}>
      {isProperty ? "🏠 Property" : "🚗 Auto"}
    </span>
  );
}

function FieldAdjusterPanel() {
  const { claims, isLoading, isLoadingMore, error, hasMore, loadMore, refresh } =
    useClaimQueue("PENDING_DAMAGE_ASSESSMENT");
  const [selectedClaim, setSelectedClaim] = useState(null);
  const [summary, setSummary] = useState("");
  const [estimatedCost, setEstimatedCost] = useState("");
  const [isBusy, setIsBusy] = useState(false);
  const [notice, setNotice] = useState("");
  const [formError, setFormError] = useState("");

  function selectClaim(claim) {
    setSelectedClaim(claim);
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
      await submitDamageAssessment(
        selectedClaim.claimId,
        { summary, estimatedCost: Number(estimatedCost) || 0 },
        selectedClaim.claimType,
      );
      setNotice(`Damage assessment submitted for ${selectedClaim.claimId}.`);
      setSelectedClaim(null);
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
        hasMore={hasMore}
        onLoadMore={loadMore}
        isLoadingMore={isLoadingMore}
        renderItem={(claim) => (
          <>
            <div className="admin-queue-item-header">
              <strong>{claim.claimId}</strong>
              <ClaimTypeBadge claimType={claim.claimType} />
              <button type="button" onClick={() => selectClaim(claim)}>
                Assess Damage
              </button>
            </div>
            <p>{claim.incidentDescription}</p>
            <div className="admin-queue-item-meta">
              {claim.claimType === "property" ? (
                <span>{claim.propertyAddress}</span>
              ) : (
                <>
                  <span>
                    {claim.vehicleYear} {claim.vehicleMake} {claim.vehicleModel}
                  </span>
                  <span>{claim.incidentLocation}</span>
                </>
              )}
            </div>
          </>
        )}
      />

      {selectedClaim && (
        <ClaimActionModal
          title={`Damage Assessment — ${selectedClaim.claimId}`}
          onClose={() => setSelectedClaim(null)}
        >
          <form className="policy-action-form" onSubmit={submitAssessment}>
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
              <button
                className="policy-form-keep"
                type="button"
                onClick={() => setSelectedClaim(null)}
                disabled={isBusy}
              >
                Cancel
              </button>
              <button type="submit" disabled={isBusy}>
                {isBusy ? "Submitting..." : "Submit Assessment"}
              </button>
            </div>
          </form>
        </ClaimActionModal>
      )}
    </div>
  );
}

function AdjusterPanel() {
  const { claims, isLoading, isLoadingMore, error, hasMore, loadMore, refresh } =
    useClaimQueue("PENDING_APPROVAL");
  const [selectedClaim, setSelectedClaim] = useState(null);
  const [approvedPayoutAmount, setApprovedPayoutAmount] = useState("");
  const [notes, setNotes] = useState("");
  const [rejectionReason, setRejectionReason] = useState("");
  const [adjusterId, setAdjusterId] = useState(DEMO_ADJUSTER_ID);
  const [isBusy, setIsBusy] = useState(false);
  const [notice, setNotice] = useState("");
  const [formError, setFormError] = useState("");

  function selectClaim(claim) {
    setSelectedClaim(claim);
    // Nullish coalescing (not ||) preserves a legitimate $0 estimated repair cost.
    setApprovedPayoutAmount(claim.estimatedRepairCost ?? "");
    setNotes("");
    setRejectionReason("");
    setAdjusterId(DEMO_ADJUSTER_ID);
    setNotice("");
    setFormError("");
  }

  async function submitApproval(event) {
    event.preventDefault();
    setIsBusy(true);
    setFormError("");
    try {
      await approveClaim(
        selectedClaim.claimId,
        { adjusterId, approvedPayoutAmount: Number(approvedPayoutAmount) || 0, notes },
        selectedClaim.claimType,
      );
      setNotice(`Approval submitted for ${selectedClaim.claimId}.`);
      setSelectedClaim(null);
      window.setTimeout(refresh, SIGNAL_REFRESH_DELAY_MS);
    } catch (submitError) {
      setFormError(submitError.message || "Unable to submit approval.");
    } finally {
      setIsBusy(false);
    }
  }

  // Denial requires a reason: the workflow records it and exits with REJECTED. Validate
  // inline (reusing formError) rather than relying on the reason textarea's own required
  // attribute, since the button that triggers this is not the form's default submit.
  async function submitDenial() {
    if (!rejectionReason.trim()) {
      setFormError("A rejection reason is required to deny a claim.");
      return;
    }
    setIsBusy(true);
    setFormError("");
    try {
      await denyClaim(
        selectedClaim.claimId,
        { adjusterId, reason: rejectionReason.trim() },
        selectedClaim.claimType,
      );
      setNotice(`Claim ${selectedClaim.claimId} denied.`);
      setSelectedClaim(null);
      window.setTimeout(refresh, SIGNAL_REFRESH_DELAY_MS);
    } catch (submitError) {
      setFormError(submitError.message || "Unable to deny claim.");
    } finally {
      setIsBusy(false);
    }
  }

  return (
    <div className="admin-tab-panel">
      <h3>✅ Claims Adjuster</h3>
      <p>
        Stand-in for the claims adjuster console. Here an adjuster reviews an assessed
        claim and either approves the payout to move it toward closure or denies the claim
        with a reason.
      </p>

      {notice && <div className="policy-modal-notice">{notice}</div>}

      <ClaimQueueList
        isLoading={isLoading}
        error={error}
        claims={claims}
        hasMore={hasMore}
        onLoadMore={loadMore}
        isLoadingMore={isLoadingMore}
        renderItem={(claim) => (
          <>
            <div className="admin-queue-item-header">
              <strong>{claim.claimId}</strong>
              <ClaimTypeBadge claimType={claim.claimType} />
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
        <ClaimActionModal title={`Review Claim — ${selectedClaim.claimId}`} onClose={() => setSelectedClaim(null)}>
          <form className="policy-action-form" onSubmit={submitApproval}>
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
              Rejection Reason
              <textarea
                value={rejectionReason}
                onChange={(event) => setRejectionReason(event.target.value)}
                placeholder="Required to deny the claim"
              />
            </label>
            <label>
              Adjuster ID
              <input type="text" value={adjusterId} onChange={(event) => setAdjusterId(event.target.value)} required />
            </label>
            {formError && <div className="policy-modal-notice policy-modal-notice--error">{formError}</div>}
            <div className="policy-form-actions">
              <button
                className="policy-form-keep"
                type="button"
                onClick={() => setSelectedClaim(null)}
                disabled={isBusy}
              >
                Cancel
              </button>
              <button
                className="admin-deny-button"
                type="button"
                onClick={submitDenial}
                disabled={isBusy}
              >
                {isBusy ? "Submitting..." : "Deny Claim"}
              </button>
              <button type="submit" disabled={isBusy}>
                {isBusy ? "Submitting..." : "Approve Payout"}
              </button>
            </div>
          </form>
        </ClaimActionModal>
      )}
    </div>
  );
}

function CATEventPanel() {
  const [eventName, setEventName] = useState(CAT_DEMO_DEFAULTS.eventName);
  const [affectedRegion, setAffectedRegion] = useState(CAT_DEMO_DEFAULTS.affectedRegion);
  const [totalClaimsToGenerate, setTotalClaimsToGenerate] = useState(
    CAT_DEMO_DEFAULTS.totalClaimsToGenerate,
  );
  // Captured at submit time so the progress header renders before the first poll returns.
  const [declaredEvent, setDeclaredEvent] = useState(null);
  const [isBusy, setIsBusy] = useState(false);
  const [formError, setFormError] = useState("");

  const activeEventId = declaredEvent?.catEventId ?? null;
  const { status, error: pollError } = useCatEventProgress(activeEventId);

  // Live preview of the id that will be declared; recomputed as the operator edits the name.
  const idPreview = generateCatEventId(eventName, new Date());

  async function declare(event) {
    event.preventDefault();
    setFormError("");

    const trimmedName = eventName.trim();
    const trimmedRegion = affectedRegion.trim();
    const total = Number(totalClaimsToGenerate);

    if (!trimmedName || !trimmedRegion) {
      setFormError("Event name and affected region are required.");
      return;
    }
    if (!Number.isInteger(total) || total < 1) {
      setFormError("Total claims to generate must be a whole number of at least 1.");
      return;
    }
    if (total > MAX_CLAIMS_PER_EVENT) {
      setFormError(`Total claims to generate must not exceed ${MAX_CLAIMS_PER_EVENT.toLocaleString()}.`);
      return;
    }

    // The id is generated at submit time from the (trimmed) name and today's date.
    const catEventId = generateCatEventId(trimmedName, new Date());
    setIsBusy(true);
    try {
      await declareCatEvent({
        catEventId,
        eventName: trimmedName,
        affectedRegion: trimmedRegion,
        totalClaimsToGenerate: total,
      });
      setDeclaredEvent({
        catEventId,
        eventName: trimmedName,
        affectedRegion: trimmedRegion,
        totalClaimsExpected: total,
      });
    } catch (declareError) {
      // Keep the form values so the operator can tweak the name (→ new id) and retry.
      setFormError(declareError.message || "Unable to declare the CAT event.");
    } finally {
      setIsBusy(false);
    }
  }

  function declareAnother() {
    setDeclaredEvent(null);
    setEventName(CAT_DEMO_DEFAULTS.eventName);
    setAffectedRegion(CAT_DEMO_DEFAULTS.affectedRegion);
    setTotalClaimsToGenerate(CAT_DEMO_DEFAULTS.totalClaimsToGenerate);
    setFormError("");
  }

  if (activeEventId) {
    const header = status ?? declaredEvent;
    const percent = catProgressPercent(status);
    const isComplete = isTerminalCatStatus(status?.status);
    const opened = status?.totalClaimsOpened ?? 0;
    const expected = status?.totalClaimsExpected ?? declaredEvent.totalClaimsExpected;

    return (
      <div className="admin-tab-panel">
        <h3>🌪️ Catastrophe Event</h3>
        <p>
          Tracking the declared event as the workflow fans out synthetic first-notice-of-loss
          property claims across the affected region.
        </p>

        <div className="cat-progress">
          <div className="cat-progress-header">
            <strong>{header.eventName}</strong>
            <span
              className={`cat-lifecycle-badge${
                isComplete ? " cat-lifecycle-badge--terminal" : ""
              }`}
            >
              {formatCatStatus(status?.status ?? "DECLARED")}
            </span>
          </div>
          <div className="admin-queue-item-meta">
            <span>{header.catEventId}</span>
            <span>{header.affectedRegion}</span>
          </div>

          <div
            className="cat-progress-bar"
            role="progressbar"
            aria-valuenow={percent}
            aria-valuemin={0}
            aria-valuemax={100}
          >
            <div className="cat-progress-fill" style={{ width: `${percent}%` }} />
          </div>
          <div className="cat-progress-caption">
            {opened} / {expected} claims filed ({percent}%)
          </div>
        </div>

        {pollError && (
          <div className="policy-modal-notice policy-modal-notice--error">{pollError}</div>
        )}

        {isComplete && (
          <>
            <div className="policy-modal-notice">
              CAT event completed — all {expected} synthetic claims filed.
            </div>
            <div className="policy-form-actions">
              <button type="button" onClick={declareAnother}>
                Declare another event
              </button>
            </div>
          </>
        )}
      </div>
    );
  }

  return (
    <div className="admin-tab-panel">
      <h3>🌪️ Catastrophe Event</h3>
      <p>
        Stand-in for the ops console that declares a regional catastrophe and mass-generates
        first-notice-of-loss property claims. Fill in the event, declare it, and watch the
        claims fan out in real time.
      </p>

      <form className="policy-action-form" onSubmit={declare}>
        <label>
          Event Name
          <input
            type="text"
            value={eventName}
            onChange={(event) => setEventName(event.target.value)}
            required
          />
        </label>
        <div className="cat-id-preview">
          Event ID: <code>{idPreview}</code>
        </div>
        <label>
          Affected Region
          <input
            type="text"
            value={affectedRegion}
            onChange={(event) => setAffectedRegion(event.target.value)}
            required
          />
        </label>
        <label>
          Total Claims to Generate
          <input
            type="number"
            min="1"
            max={MAX_CLAIMS_PER_EVENT}
            value={totalClaimsToGenerate}
            onChange={(event) => setTotalClaimsToGenerate(event.target.value)}
            required
          />
        </label>
        {formError && <div className="policy-modal-notice policy-modal-notice--error">{formError}</div>}
        <div className="policy-form-actions">
          <button type="submit" disabled={isBusy}>
            {isBusy ? "Declaring..." : "Declare CAT Event"}
          </button>
        </div>
      </form>
    </div>
  );
}

const TAB_PANELS = {
  "field-adjuster": FieldAdjusterPanel,
  adjuster: AdjusterPanel,
  "cat-event": CATEventPanel,
};

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
                {(() => {
                  const ActivePanel = TAB_PANELS[activeTab] ?? FieldAdjusterPanel;
                  return <ActivePanel />;
                })()}
              </div>
            </div>
          </section>
        </div>
      )}
    </>
  );
}

export default AdminPanel;
