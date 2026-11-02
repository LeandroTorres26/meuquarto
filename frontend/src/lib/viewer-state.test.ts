import { describe, expect, it } from "vitest";
import { profileHref, viewerState } from "./viewer-state";

describe("viewerState", () => {
  it("is visitor without a session, on any page", () => {
    expect(viewerState("leandro", null)).toBe("visitor");
    expect(viewerState(undefined, null)).toBe("visitor");
  });

  it("is own when the page slug is the session slug, ignoring case", () => {
    expect(viewerState("leandro", { slug: "leandro" })).toBe("own");
    expect(viewerState("Leandro", { slug: "leandro" })).toBe("own");
  });

  it("is other on someone else's page", () => {
    expect(viewerState("maria", { slug: "leandro" })).toBe("other");
  });

  it("is other on pages with no owner", () => {
    expect(viewerState(undefined, { slug: "leandro" })).toBe("other");
    expect(viewerState(null, { slug: "leandro" })).toBe("other");
  });

  it("is other when the session has no slug yet, never own", () => {
    expect(viewerState("leandro", { slug: null })).toBe("other");
  });
});

describe("profileHref", () => {
  it("encodes the slug", () => {
    expect(profileHref("leandro")).toBe("/leandro");
    expect(profileHref("a b")).toBe("/a%20b");
  });
});
