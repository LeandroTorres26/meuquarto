import { describe, expect, it } from "vitest";
import {
  acknowledgeCookieNotice,
  COOKIE_NOTICE_KEY,
  shouldShowCookieNotice,
} from "./cookie-notice";

function memoryStorage(initial: Record<string, string> = {}) {
  const data = { ...initial };
  return {
    data,
    getItem: (key: string) => data[key] ?? null,
    setItem: (key: string, value: string) => {
      data[key] = value;
    },
  };
}

const broken = {
  getItem: () => {
    throw new Error("blocked");
  },
  setItem: () => {
    throw new Error("blocked");
  },
};

describe("cookie notice", () => {
  it("shows on the first visit and not after it was acknowledged", () => {
    const storage = memoryStorage();
    expect(shouldShowCookieNotice(storage)).toBe(true);
    acknowledgeCookieNotice(storage);
    expect(storage.data[COOKIE_NOTICE_KEY]).toBe("ok");
    expect(shouldShowCookieNotice(storage)).toBe(false);
  });

  it("shows again with ?cookies=1 even when acknowledged", () => {
    const storage = memoryStorage({ [COOKIE_NOTICE_KEY]: "ok" });
    expect(shouldShowCookieNotice(storage, "?cookies=1")).toBe(true);
    expect(shouldShowCookieNotice(storage, "?cookies=0")).toBe(false);
  });

  it("works without storage: shows, and acknowledging does not throw", () => {
    expect(shouldShowCookieNotice(null)).toBe(true);
    expect(shouldShowCookieNotice(broken)).toBe(true);
    expect(() => acknowledgeCookieNotice(broken)).not.toThrow();
    expect(() => acknowledgeCookieNotice(null)).not.toThrow();
  });
});
