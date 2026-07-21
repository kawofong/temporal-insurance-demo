import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  ADJUSTER_QUEUE_PAGE_SIZE,
  CLAIM_ENDPOINT,
  CLAIM_ENDPOINTS,
  approveClaim,
  denyClaim,
  claimStatusClass,
  fetchClaim,
  formatClaimStatus,
  formatCurrency,
  isTerminalClaimStatus,
  listAdjusterQueue,
  listClaims,
  listClaimsPage,
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

  it("treats only CLOSED and REJECTED as terminal", () => {
    expect(isTerminalClaimStatus("CLOSED")).toBe(true);
    expect(isTerminalClaimStatus("REJECTED")).toBe(true);
    expect(isTerminalClaimStatus("SUBMITTED")).toBe(false);
    expect(isTerminalClaimStatus("PAYMENT_PROCESSING")).toBe(false);
    expect(isTerminalClaimStatus("SOMETHING_NEW")).toBe(false);
    expect(isTerminalClaimStatus(undefined)).toBe(false);
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
      adjusterId: "adj-sarah",
      approvedPayoutAmount: 1200,
      notes: "Looks good",
    });

    expect(global.fetch).toHaveBeenCalledWith(
      `${CLAIM_ENDPOINT}/CLM-ABC/approve`,
      expect.objectContaining({
        method: "POST",
        body: JSON.stringify({ adjusterId: "adj-sarah", approvedPayoutAmount: 1200, notes: "Looks good" }),
      }),
    );
  });

  it("denyClaim posts the adjuster denial", async () => {
    global.fetch.mockResolvedValue(jsonResponse({}, true, 202));

    await denyClaim("CLM-ABC", {
      adjusterId: "adj-sarah",
      reason: "Damage below deductible",
    });

    expect(global.fetch).toHaveBeenCalledWith(
      `${CLAIM_ENDPOINT}/CLM-ABC/deny`,
      expect.objectContaining({
        method: "POST",
        body: JSON.stringify({ adjusterId: "adj-sarah", reason: "Damage below deductible" }),
      }),
    );
  });

  it("throws a friendly error when a claim is not found", async () => {
    global.fetch.mockResolvedValue(jsonResponse({ error: "NOT_FOUND", message: "Claim not found" }, false, 404));

    await expect(fetchClaim("DOES-NOT-EXIST")).rejects.toThrow("Claim not found");
  });
});

describe("per-type endpoint routing", () => {
  beforeEach(() => {
    global.fetch = vi.fn();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("defaults to the auto endpoint when no claim type is given", async () => {
    global.fetch.mockResolvedValue(jsonResponse({ claims: [] }));

    await listClaims({ status: "PENDING_APPROVAL" });

    expect(global.fetch).toHaveBeenCalledWith(
      `${CLAIM_ENDPOINTS.auto}?status=PENDING_APPROVAL`,
      undefined,
    );
  });

  it("routes listClaims to the property endpoint for property claims", async () => {
    global.fetch.mockResolvedValue(jsonResponse({ claims: [] }));

    await listClaims({ status: "PENDING_APPROVAL" }, undefined, "property");

    expect(global.fetch).toHaveBeenCalledWith(
      `${CLAIM_ENDPOINTS.property}?status=PENDING_APPROVAL`,
      undefined,
    );
  });

  it("routes fetchClaim to the property endpoint for property claims", async () => {
    global.fetch.mockResolvedValue(jsonResponse({ claimId: "CLM-ABC" }));

    await fetchClaim("CLM-ABC", undefined, "property");

    expect(global.fetch).toHaveBeenCalledWith(`${CLAIM_ENDPOINTS.property}/CLM-ABC`, undefined);
  });

  it("routes submitDamageAssessment to the property endpoint for property claims", async () => {
    global.fetch.mockResolvedValue(jsonResponse({}, true, 202));

    await submitDamageAssessment("CLM-ABC", { summary: "Roof damage", estimatedCost: 8000 }, "property");

    expect(global.fetch).toHaveBeenCalledWith(
      `${CLAIM_ENDPOINTS.property}/CLM-ABC/damage-assessment`,
      expect.objectContaining({ method: "POST" }),
    );
  });

  it("routes approveClaim to the property endpoint for property claims", async () => {
    global.fetch.mockResolvedValue(jsonResponse({}, true, 202));

    await approveClaim("CLM-ABC", { adjusterId: "adj-sarah", approvedPayoutAmount: 8000, notes: "" }, "property");

    expect(global.fetch).toHaveBeenCalledWith(
      `${CLAIM_ENDPOINTS.property}/CLM-ABC/approve`,
      expect.objectContaining({ method: "POST" }),
    );
  });
});

describe("listClaimsPage", () => {
  beforeEach(() => {
    global.fetch = vi.fn();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("returns the claims and the next page token", async () => {
    global.fetch.mockResolvedValue(jsonResponse({ claims: [{ claimId: "CLM-1" }], nextPageToken: "TOK-2" }));

    const result = await listClaimsPage({ status: "PENDING_APPROVAL", pageSize: 100 });

    expect(global.fetch).toHaveBeenCalledWith(
      `${CLAIM_ENDPOINTS.auto}?status=PENDING_APPROVAL&pageSize=100`,
      undefined,
    );
    expect(result).toEqual({ claims: [{ claimId: "CLM-1" }], nextPageToken: "TOK-2" });
  });

  it("forwards the page token and routes by claim type", async () => {
    global.fetch.mockResolvedValue(jsonResponse({ claims: [], nextPageToken: null }));

    const result = await listClaimsPage({ status: "PENDING_APPROVAL", pageSize: 100, pageToken: "TOK-2" }, undefined, "property");

    expect(global.fetch).toHaveBeenCalledWith(
      `${CLAIM_ENDPOINTS.property}?status=PENDING_APPROVAL&pageSize=100&pageToken=TOK-2`,
      undefined,
    );
    expect(result).toEqual({ claims: [], nextPageToken: null });
  });

  it("normalizes a missing next page token to null", async () => {
    global.fetch.mockResolvedValue(jsonResponse({ claims: [{ claimId: "CLM-1" }] }));

    const result = await listClaimsPage({ status: "PENDING_APPROVAL" });

    expect(result.nextPageToken).toBeNull();
  });
});

describe("listAdjusterQueue", () => {
  beforeEach(() => {
    global.fetch = vi.fn();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("requests one page of each type at the default page size, tagged and with per-type tokens", async () => {
    global.fetch.mockImplementation((url) => {
      if (url.startsWith(CLAIM_ENDPOINTS.auto)) {
        return Promise.resolve(jsonResponse({ claims: [{ claimId: "AUTO-1" }], nextPageToken: "AUTO-TOK" }));
      }
      return Promise.resolve(jsonResponse({ claims: [{ claimId: "PROP-1" }], nextPageToken: null }));
    });

    const result = await listAdjusterQueue("PENDING_APPROVAL");

    expect(global.fetch).toHaveBeenCalledWith(
      `${CLAIM_ENDPOINTS.auto}?status=PENDING_APPROVAL&pageSize=${ADJUSTER_QUEUE_PAGE_SIZE}`,
      undefined,
    );
    expect(global.fetch).toHaveBeenCalledWith(
      `${CLAIM_ENDPOINTS.property}?status=PENDING_APPROVAL&pageSize=${ADJUSTER_QUEUE_PAGE_SIZE}`,
      undefined,
    );
    expect(result).toEqual({
      claims: [
        { claimId: "AUTO-1", claimType: "auto" },
        { claimId: "PROP-1", claimType: "property" },
      ],
      autoPageToken: "AUTO-TOK",
      propertyPageToken: null,
    });
  });

  it("fetches the next page with the given token and skips an exhausted (null-token) source", async () => {
    global.fetch.mockResolvedValue(jsonResponse({ claims: [{ claimId: "AUTO-2" }], nextPageToken: null }));

    const result = await listAdjusterQueue("PENDING_APPROVAL", {
      autoPageToken: "AUTO-TOK",
      propertyPageToken: null,
    });

    expect(global.fetch).toHaveBeenCalledTimes(1);
    expect(global.fetch).toHaveBeenCalledWith(
      `${CLAIM_ENDPOINTS.auto}?status=PENDING_APPROVAL&pageSize=${ADJUSTER_QUEUE_PAGE_SIZE}&pageToken=AUTO-TOK`,
      undefined,
    );
    expect(global.fetch).not.toHaveBeenCalledWith(
      expect.stringContaining(CLAIM_ENDPOINTS.property),
      expect.anything(),
    );
    expect(result).toEqual({
      claims: [{ claimId: "AUTO-2", claimType: "auto" }],
      autoPageToken: null,
      propertyPageToken: null,
    });
  });

  it("surfaces the error when one endpoint fails", async () => {
    global.fetch.mockImplementation((url) => {
      if (url.startsWith(CLAIM_ENDPOINTS.auto)) {
        return Promise.resolve(jsonResponse({ claims: [] }));
      }
      return Promise.resolve(jsonResponse({ error: "BOOM", message: "Property queue unavailable" }, false, 500));
    });

    await expect(listAdjusterQueue("PENDING_APPROVAL")).rejects.toThrow("Property queue unavailable");
  });
});
