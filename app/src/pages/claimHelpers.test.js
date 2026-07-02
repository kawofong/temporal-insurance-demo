import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  CLAIM_ENDPOINT,
  approveClaim,
  claimStatusClass,
  fetchClaim,
  formatClaimStatus,
  formatCurrency,
  listClaims,
  submitDamageAssessment,
  submitFnol,
} from "./claimHelpers";

function jsonResponse(body, ok = true, status = 200) {
  return {
    ok,
    status,
    json: () => Promise.resolve(body),
  };
}

describe("status mapping", () => {
  it("formats known statuses to their labels", () => {
    expect(formatClaimStatus("SUBMITTED")).toBe("Submitted");
    expect(formatClaimStatus("PENDING_DAMAGE_ASSESSMENT")).toBe("Awaiting Damage Assessment");
    expect(formatClaimStatus("PENDING_APPROVAL")).toBe("Awaiting Approval");
    expect(formatClaimStatus("PAYMENT_PROCESSING")).toBe("Processing Payment");
    expect(formatClaimStatus("CLOSED")).toBe("Closed / Paid");
    expect(formatClaimStatus("REJECTED")).toBe("Rejected");
  });

  it("falls back to the raw value for an unknown status", () => {
    expect(formatClaimStatus("SOMETHING_NEW")).toBe("SOMETHING_NEW");
  });

  it("maps statuses to the existing CSS classes", () => {
    expect(claimStatusClass("SUBMITTED")).toBe("in-review");
    expect(claimStatusClass("PAYMENT_PROCESSING")).toBe("approved");
    expect(claimStatusClass("CLOSED")).toBe("paid");
    expect(claimStatusClass("REJECTED")).toBe("denied");
  });
});

describe("formatCurrency", () => {
  it("formats an integer amount as USD", () => {
    expect(formatCurrency(4200)).toBe("$4,200");
  });

  it("returns an em dash for missing amounts", () => {
    expect(formatCurrency(null)).toBe("—");
    expect(formatCurrency(undefined)).toBe("—");
    expect(formatCurrency("")).toBe("—");
  });
});

describe("claimHelpers fetch client", () => {
  beforeEach(() => {
    global.fetch = vi.fn();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("submitFnol posts the payload and returns the FnolResponse", async () => {
    const payload = { policyId: "demo-auto-001", policyHolderId: "PH-001" };
    global.fetch.mockResolvedValue(jsonResponse({ claimId: "CLM-ABC", status: "SUBMITTED" }));

    const result = await submitFnol(payload);

    expect(global.fetch).toHaveBeenCalledWith(
      CLAIM_ENDPOINT,
      expect.objectContaining({ method: "POST", body: JSON.stringify(payload) }),
    );
    expect(result).toEqual({ claimId: "CLM-ABC", status: "SUBMITTED" });
  });

  it("submitFnol surfaces the API error message on failure", async () => {
    global.fetch.mockResolvedValue(
      jsonResponse({ error: "VALIDATION_FAILED", message: "vehicleVin is required" }, false, 400),
    );

    await expect(submitFnol({})).rejects.toThrow("vehicleVin is required");
  });

  it("fetchClaim GETs a single claim by id", async () => {
    global.fetch.mockResolvedValue(jsonResponse({ claimId: "CLM-ABC" }));

    const result = await fetchClaim("CLM-ABC");

    expect(global.fetch).toHaveBeenCalledWith(`${CLAIM_ENDPOINT}/CLM-ABC`, undefined);
    expect(result).toEqual({ claimId: "CLM-ABC" });
  });

  it("listClaims builds the query string from provided filters", async () => {
    global.fetch.mockResolvedValue(jsonResponse({ claims: [{ claimId: "CLM-ABC" }] }));

    const result = await listClaims({ policyHolderId: "PH-001", status: "PENDING_APPROVAL" });

    expect(global.fetch).toHaveBeenCalledWith(
      `${CLAIM_ENDPOINT}?policyHolderId=PH-001&status=PENDING_APPROVAL`,
      undefined,
    );
    expect(result).toEqual([{ claimId: "CLM-ABC" }]);
  });

  it("listClaims omits the query string when no filters are given", async () => {
    global.fetch.mockResolvedValue(jsonResponse({ claims: [] }));

    await listClaims();

    expect(global.fetch).toHaveBeenCalledWith(CLAIM_ENDPOINT, undefined);
  });

  it("submitDamageAssessment posts the summary and estimated cost", async () => {
    global.fetch.mockResolvedValue(jsonResponse({}, true, 202));

    await submitDamageAssessment("CLM-ABC", { summary: "Dented bumper", estimatedCost: 1200 });

    expect(global.fetch).toHaveBeenCalledWith(
      `${CLAIM_ENDPOINT}/CLM-ABC/damage-assessment`,
      expect.objectContaining({
        method: "POST",
        body: JSON.stringify({ summary: "Dented bumper", estimatedCost: 1200 }),
      }),
    );
  });

  it("approveClaim posts the adjuster decision", async () => {
    global.fetch.mockResolvedValue(jsonResponse({}, true, 202));

    await approveClaim("CLM-ABC", {
      adjusterId: "ADJ-SARAH",
      approvedPayoutAmount: 1200,
      notes: "Looks good",
    });

    expect(global.fetch).toHaveBeenCalledWith(
      `${CLAIM_ENDPOINT}/CLM-ABC/approve`,
      expect.objectContaining({
        method: "POST",
        body: JSON.stringify({ adjusterId: "ADJ-SARAH", approvedPayoutAmount: 1200, notes: "Looks good" }),
      }),
    );
  });

  it("throws a friendly error when a claim is not found", async () => {
    global.fetch.mockResolvedValue(jsonResponse({ error: "NOT_FOUND", message: "Claim not found" }, false, 404));

    await expect(fetchClaim("DOES-NOT-EXIST")).rejects.toThrow("Claim not found");
  });
});
