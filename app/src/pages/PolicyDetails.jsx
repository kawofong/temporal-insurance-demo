/* Policy details page */
/* Full-page view of a single policy with add-line-item and cancel pop-ups */

import { useCallback, useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import AdminPanel from "./AdminPanel";
import { policyholder } from "../data/mockData";
import { submitFnol } from "./claimHelpers";
import {
  ADD_ACTIONS,
  POLICY_ENDPOINT,
  SIGNAL_REFRESH_DELAY_MS,
  canAddToPolicy,
  formatFieldLabel,
  formatFieldValue,
  getPolicyIcon,
  getPolicySummary,
  isCancelled,
  readApiError,
  titleCase,
} from "./policyHelpers";
import "./Portal.css";
import "./PolicyDetails.css";

// FNOL fields pre-filled from the policy's primary insured vehicle, editable in the form.
function initialFnolValues(policy) {
  const vehicle = policy?.insuredVehicles?.[0] || {};
  return {
    incidentDescription: "",
    incidentDate: "",
    incidentLocation: "",
    vehicleVin: vehicle.vin || "",
    vehicleMake: vehicle.make || "",
    vehicleModel: vehicle.model || "",
    vehicleYear: vehicle.year || "",
  };
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

// Generic pop-up shell reused by the add and cancel interactions.
function Modal({ title, subtitle, onClose, children }) {
  return (
    <div className="policy-modal-backdrop" role="presentation" onClick={onClose}>
      <section
        className="policy-modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="policy-action-modal-title"
        onClick={(event) => event.stopPropagation()}
      >
        <div className="policy-modal-header">
          <div>
            <h3 id="policy-action-modal-title">{title}</h3>
            {subtitle && <p>{subtitle}</p>}
          </div>
          <button className="policy-modal-close" type="button" onClick={onClose} aria-label="Close">
            ×
          </button>
        </div>
        {children}
      </section>
    </div>
  );
}

function PolicyDetails() {
  const { policyType, policyId } = useParams();
  const navigate = useNavigate();

  const [policy, setPolicy] = useState(null);
  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState("");

  // Pop-up state: which modal is open, plus its form values and status.
  const [activeModal, setActiveModal] = useState(null); // "add" | "cancel" | "fnol" | null
  const [addValues, setAddValues] = useState({});
  const [cancelReason, setCancelReason] = useState("");
  const [fnolValues, setFnolValues] = useState({});
  const [isBusy, setIsBusy] = useState(false);
  const [notice, setNotice] = useState("");
  const [actionError, setActionError] = useState("");

  const basePath = `${POLICY_ENDPOINT}/${policyType}/${policyId}`;
  const addAction = ADD_ACTIONS[policyType];

  const loadPolicy = useCallback(
    async (signal) => {
      setIsLoading(true);
      try {
        const response = await fetch(basePath, signal ? { signal } : undefined);
        if (!response.ok) {
          throw new Error(await readApiError(response, `Policy API returned ${response.status}`));
        }
        const data = await response.json();
        setPolicy({ ...data, policyType });
        setLoadError("");
      } catch (error) {
        if (error.name !== "AbortError") {
          setLoadError(error.message || "Unable to load this policy.");
        }
      } finally {
        if (!signal || !signal.aborted) setIsLoading(false);
      }
    },
    [basePath, policyType],
  );

  useEffect(() => {
    const controller = new AbortController();
    loadPolicy(controller.signal);
    return () => controller.abort();
  }, [loadPolicy]);

  function closeModal() {
    setActiveModal(null);
    setActionError("");
    setIsBusy(false);
  }

  function openAdd() {
    setActionError("");
    setNotice("");
    setAddValues(addAction?.initialValues || {});
    setActiveModal("add");
  }

  function openCancel() {
    setActionError("");
    setNotice("");
    setCancelReason("");
    setActiveModal("cancel");
  }

  function openFnol() {
    setActionError("");
    setNotice("");
    setFnolValues(initialFnolValues(policy));
    setActiveModal("fnol");
  }

  // Re-fetches the policy after a mutation so the page reflects new state.
  async function refreshPolicy(successMessage) {
    try {
      await loadPolicy();
      if (successMessage) setNotice(successMessage);
    } catch {
      setActionError("Unable to refresh policy.");
    }
  }

  async function submitAdd(event) {
    event.preventDefault();
    setIsBusy(true);
    setActionError("");
    setNotice("");
    try {
      const response = await fetch(`${basePath}/${addAction.endpoint}`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(addAction.buildPayload(addValues)),
      });
      if (!response.ok) throw new Error(await readApiError(response, `Add returned ${response.status}`));
      closeModal();
      if (addAction.kind === "signal") {
        // Signals are fire-and-forget (202, no body); state updates asynchronously.
        setNotice(addAction.successMessage);
        window.setTimeout(() => refreshPolicy(addAction.successMessage), SIGNAL_REFRESH_DELAY_MS);
      } else {
        // Updates are synchronous and return the new line-item count.
        const data = await response.json();
        await refreshPolicy(`${addAction.successLabel}: ${data.count}`);
      }
    } catch (addError) {
      setActionError(addError.message || `Unable to ${addAction.label.toLowerCase()}.`);
      setIsBusy(false);
    }
  }

  async function submitCancel(event) {
    event.preventDefault();
    if (!cancelReason.trim()) {
      setActionError("Cancellation reason is required.");
      return;
    }
    setIsBusy(true);
    setActionError("");
    setNotice("");
    try {
      const response = await fetch(`${basePath}/cancel`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ reason: cancelReason.trim() }),
      });
      if (!response.ok) throw new Error(await readApiError(response, `Cancel returned ${response.status}`));
      closeModal();
      setNotice("Cancellation submitted. Refreshing policy status...");
      window.setTimeout(() => refreshPolicy(), SIGNAL_REFRESH_DELAY_MS);
    } catch (cancelError) {
      setActionError(cancelError.message || "Unable to cancel policy.");
      setIsBusy(false);
    }
  }

  async function submitFnolClaim(event) {
    event.preventDefault();
    if (!fnolValues.vehicleVin?.trim()) {
      setActionError("Vehicle VIN is required.");
      return;
    }
    setIsBusy(true);
    setActionError("");
    setNotice("");
    try {
      const response = await submitFnol({
        policyId,
        policyHolderId: policyholder.memberId,
        incidentDescription: fnolValues.incidentDescription,
        incidentDate: new Date(fnolValues.incidentDate).getTime(),
        incidentLocation: fnolValues.incidentLocation,
        vehicleVin: fnolValues.vehicleVin,
        vehicleMake: fnolValues.vehicleMake,
        vehicleModel: fnolValues.vehicleModel,
        vehicleYear: Number(fnolValues.vehicleYear) || 0,
      });
      // Early return: the workflow just started, so we route straight to its detail
      // view without waiting for back-office processing.
      navigate(`/portal/claims/${response.claimId}`);
    } catch (fnolError) {
      setActionError(fnolError.message || "Unable to submit claim.");
      setIsBusy(false);
    }
  }

  const cancelled = isCancelled(policy);
  const addEnabled = Boolean(addAction) && canAddToPolicy(policy);
  // Require the policy to have actually loaded: isCancelled(null) is false, so without this
  // guard a failed fetch on a genuinely cancelled policy would leave the button enabled.
  const fnolEnabled = policyType === "auto" && policy != null && !cancelled;

  return (
    <div className="portal-page">
      <nav className="details-back">
        <button className="back-link" type="button" onClick={() => navigate("/portal")}>
          ← Back to dashboard
        </button>
      </nav>

      <header className="portal-header">
        <div className="portal-header-left">
          <h1>
            {getPolicyIcon(policyType)} {titleCase(policyType)} Policy
          </h1>
          <p>{policyId}</p>
        </div>
        <div className="portal-header-actions">
          <button
            className="claim-button"
            type="button"
            onClick={openFnol}
            disabled={!fnolEnabled}
            title={fnolEnabled ? "" : "Claims are only available for auto policies"}
          >
            File a Claim
          </button>
        </div>
      </header>

      {isLoading ? (
        <div className="policies-placeholder">Loading policy...</div>
      ) : loadError ? (
        <div className="policies-placeholder policies-placeholder--error">{loadError}</div>
      ) : (
        <>
          <section>
            <h2 className="section-title">Summary</h2>
            <div className="policy-modal-summary">
              {getPolicySummary(policy).map((item) => (
                <div className="modal-summary-tile" key={item.label}>
                  <span>{item.label}</span>
                  <strong>{item.value}</strong>
                </div>
              ))}
            </div>
          </section>

          <section>
            <h2 className="section-title">Details</h2>
            <div className="policy-details-panel">
              <DetailRows data={policy} exclude={["policyType"]} />
            </div>
          </section>

          {notice && <div className="policy-modal-notice">{notice}</div>}

          <div className="policy-modal-actions">
            {addAction && !cancelled && (
              <button type="button" onClick={openAdd} disabled={!addEnabled || isBusy}>
                {addAction.label}
              </button>
            )}
            {!cancelled && (
              <button
                className="policy-modal-danger policy-modal-cancel"
                type="button"
                onClick={openCancel}
                disabled={isBusy}
              >
                Cancel Policy
              </button>
            )}
          </div>
        </>
      )}

      {activeModal === "add" && addAction && (
        <Modal title={addAction.label} subtitle={policyId} onClose={closeModal}>
          <form className="policy-action-form" onSubmit={submitAdd}>
            {addAction.fields.map((field) => (
              <label key={field.name}>
                {field.label}
                <input
                  type={field.type || "text"}
                  value={addValues[field.name] || ""}
                  onChange={(event) =>
                    setAddValues((values) => ({ ...values, [field.name]: event.target.value }))
                  }
                  required
                />
              </label>
            ))}
            {actionError && <div className="policy-modal-notice policy-modal-notice--error">{actionError}</div>}
            <div className="policy-form-actions">
              <button type="submit" disabled={isBusy}>
                {isBusy ? "Submitting..." : "Submit"}
              </button>
            </div>
          </form>
        </Modal>
      )}

      {activeModal === "cancel" && (
        <Modal title="Cancel Policy" subtitle={policyId} onClose={closeModal}>
          <form className="policy-action-form policy-action-form--danger" onSubmit={submitCancel}>
            <p>Cancelling is destructive, irreversible, and completes the policy workflow.</p>
            <label>
              Reason
              <textarea
                value={cancelReason}
                onChange={(event) => setCancelReason(event.target.value)}
                required
              />
            </label>
            {actionError && <div className="policy-modal-notice policy-modal-notice--error">{actionError}</div>}
            <div className="policy-form-actions">
              <button className="policy-form-keep" type="button" onClick={closeModal} disabled={isBusy}>
                Keep Policy
              </button>
              <button className="policy-form-confirm" type="submit" disabled={isBusy}>
                {isBusy ? "Cancelling..." : "Confirm Cancel"}
              </button>
            </div>
          </form>
        </Modal>
      )}

      {activeModal === "fnol" && (
        <Modal title="File a Claim" subtitle={policyId} onClose={closeModal}>
          <form className="policy-action-form" onSubmit={submitFnolClaim}>
            <label>
              What happened?
              <textarea
                value={fnolValues.incidentDescription || ""}
                onChange={(event) =>
                  setFnolValues((values) => ({ ...values, incidentDescription: event.target.value }))
                }
                required
              />
            </label>
            <label>
              Date of incident
              <input
                type="date"
                value={fnolValues.incidentDate || ""}
                onChange={(event) => setFnolValues((values) => ({ ...values, incidentDate: event.target.value }))}
                required
              />
            </label>
            <label>
              Location
              <input
                type="text"
                value={fnolValues.incidentLocation || ""}
                onChange={(event) =>
                  setFnolValues((values) => ({ ...values, incidentLocation: event.target.value }))
                }
                required
              />
            </label>
            <label>
              Vehicle VIN
              <input
                type="text"
                value={fnolValues.vehicleVin || ""}
                onChange={(event) => setFnolValues((values) => ({ ...values, vehicleVin: event.target.value }))}
                required
              />
            </label>
            <label>
              Make
              <input
                type="text"
                value={fnolValues.vehicleMake || ""}
                onChange={(event) => setFnolValues((values) => ({ ...values, vehicleMake: event.target.value }))}
              />
            </label>
            <label>
              Model
              <input
                type="text"
                value={fnolValues.vehicleModel || ""}
                onChange={(event) => setFnolValues((values) => ({ ...values, vehicleModel: event.target.value }))}
              />
            </label>
            <label>
              Year
              <input
                type="number"
                value={fnolValues.vehicleYear || ""}
                onChange={(event) => setFnolValues((values) => ({ ...values, vehicleYear: event.target.value }))}
              />
            </label>
            {actionError && <div className="policy-modal-notice policy-modal-notice--error">{actionError}</div>}
            <div className="policy-form-actions">
              <button type="submit" disabled={isBusy}>
                {isBusy ? "Submitting..." : "Submit Claim"}
              </button>
            </div>
          </form>
        </Modal>
      )}

      <footer className="portal-footer">Ziggy Insurance ★ Policyholder Portal ★ v1.0</footer>

      <AdminPanel />
    </div>
  );
}

export default PolicyDetails;
