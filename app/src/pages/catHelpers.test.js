import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  CAT_ENDPOINT,
  catProgressPercent,
  declareCatEvent,
  fetchCatEventStatus,
  formatCatStatus,
  generateCatEventId,
  isTerminalCatStatus,
  slugify,
} from "./catHelpers";

function jsonResponse(body, ok = true, status = 200) {
  return {
    ok,
    status,
    json: () => Promise.resolve(body),
  };
}

describe("status mapping", () => {
  it("formats each lifecycle value to its label", () => {
    expect(formatCatStatus("DECLARED")).toBe("Declared");
    expect(formatCatStatus("SPAWNING")).toBe("Spawning claims");
    expect(formatCatStatus("COMPLETED")).toBe("Completed");
  });

  it("falls back for unknown/empty status", () => {
    expect(formatCatStatus("SOMETHING_NEW")).toBe("SOMETHING_NEW");
    expect(formatCatStatus(undefined)).toBe("Unknown");
    expect(formatCatStatus("")).toBe("Unknown");
  });

  it("treats only COMPLETED as terminal", () => {
    expect(isTerminalCatStatus("COMPLETED")).toBe(true);
    expect(isTerminalCatStatus("DECLARED")).toBe(false);
    expect(isTerminalCatStatus("SPAWNING")).toBe(false);
    expect(isTerminalCatStatus("SOMETHING_NEW")).toBe(false);
    expect(isTerminalCatStatus(undefined)).toBe(false);
  });
});

describe("catProgressPercent", () => {
  it("clamps and rounds percentComplete to an integer 0..100", () => {
    expect(catProgressPercent({ percentComplete: 0 })).toBe(0);
    expect(catProgressPercent({ percentComplete: 42.4 })).toBe(42);
    expect(catProgressPercent({ percentComplete: 42.6 })).toBe(43);
    expect(catProgressPercent({ percentComplete: 100 })).toBe(100);
    expect(catProgressPercent({ percentComplete: 150 })).toBe(100);
    expect(catProgressPercent({ percentComplete: -10 })).toBe(0);
  });

  it("returns 0 for a missing status or percent", () => {
    expect(catProgressPercent(undefined)).toBe(0);
    expect(catProgressPercent(null)).toBe(0);
    expect(catProgressPercent({})).toBe(0);
  });
});

describe("slugify", () => {
  it("lowercases and collapses non-alphanumeric runs to a single dash", () => {
    expect(slugify("Butte County Wildfire")).toBe("butte-county-wildfire");
    expect(slugify("Nor'easter — 2026!")).toBe("nor-easter-2026");
  });

  it("trims leading/trailing dashes and collapses multi-space", () => {
    expect(slugify("  Hello   World  ")).toBe("hello-world");
    expect(slugify("---Already-Slugged---")).toBe("already-slugged");
  });

  it("returns an empty string for punctuation-only or empty input", () => {
    expect(slugify("!!!")).toBe("");
    expect(slugify("")).toBe("");
  });
});

describe("generateCatEventId", () => {
  it("produces evt-YYYY-MM-DD-<slug> with zero-padded month/day", () => {
    const date = new Date(2026, 6, 10); // local July 10, 2026
    expect(generateCatEventId("Butte County Wildfire", date)).toBe(
      "evt-2026-07-10-butte-county-wildfire",
    );
  });

  it("falls back to ...-event for a punctuation-only name", () => {
    const date = new Date(2026, 6, 10);
    expect(generateCatEventId("!!!", date)).toBe("evt-2026-07-10-event");
  });
});

describe("catHelpers fetch client", () => {
  beforeEach(() => {
    global.fetch = vi.fn();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("declareCatEvent posts exactly the four request fields (no batchSize)", async () => {
    global.fetch.mockResolvedValue(
      jsonResponse({ catEventId: "evt-2026-07-10-butte-county-wildfire", status: "DECLARED", totalClaimsExpected: 5 }, true, 201),
    );

    const result = await declareCatEvent({
      catEventId: "evt-2026-07-10-butte-county-wildfire",
      eventName: "Butte County Wildfire",
      affectedRegion: "Northern California",
      totalClaimsToGenerate: 5,
    });

    expect(global.fetch).toHaveBeenCalledWith(
      CAT_ENDPOINT,
      expect.objectContaining({
        method: "POST",
        body: JSON.stringify({
          catEventId: "evt-2026-07-10-butte-county-wildfire",
          eventName: "Butte County Wildfire",
          affectedRegion: "Northern California",
          totalClaimsToGenerate: 5,
        }),
      }),
    );
    const sentBody = JSON.parse(global.fetch.mock.calls[0][1].body);
    expect(sentBody).not.toHaveProperty("batchSize");
    expect(result).toEqual({
      catEventId: "evt-2026-07-10-butte-county-wildfire",
      status: "DECLARED",
      totalClaimsExpected: 5,
    });
  });

  it("declareCatEvent surfaces the API error message on failure", async () => {
    global.fetch.mockResolvedValue(
      jsonResponse({ error: "VALIDATION_FAILED", message: "totalClaimsToGenerate must be positive" }, false, 400),
    );

    await expect(
      declareCatEvent({
        catEventId: "evt-x",
        eventName: "X",
        affectedRegion: "Y",
        totalClaimsToGenerate: 0,
      }),
    ).rejects.toThrow("totalClaimsToGenerate must be positive");
  });

  it("fetchCatEventStatus GETs the event by id", async () => {
    global.fetch.mockResolvedValue(
      jsonResponse({ catEventId: "evt-abc", status: "SPAWNING", percentComplete: 40 }),
    );

    const result = await fetchCatEventStatus("evt-abc");

    expect(global.fetch).toHaveBeenCalledWith(`${CAT_ENDPOINT}/evt-abc`, undefined);
    expect(result).toEqual({ catEventId: "evt-abc", status: "SPAWNING", percentComplete: 40 });
  });

  it("fetchCatEventStatus throws a friendly error when not found", async () => {
    global.fetch.mockResolvedValue(
      jsonResponse({ error: "NOT_FOUND", message: "CAT event not found" }, false, 404),
    );

    await expect(fetchCatEventStatus("nope")).rejects.toThrow("CAT event not found");
  });
});
