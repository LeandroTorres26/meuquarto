import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { debounce, normalizeSlug } from "./slug";

describe("normalizeSlug", () => {
  it("trims and lowercases like the backend", () => {
    expect(normalizeSlug("  LeAndro-1 ")).toBe("leandro-1");
    expect(normalizeSlug("")).toBe("");
  });
});

describe("debounce", () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  it("calls once with the last arguments after the wait", () => {
    const fn = vi.fn();
    const debounced = debounce(fn, 300);
    debounced("a");
    vi.advanceTimersByTime(200);
    debounced("ab");
    vi.advanceTimersByTime(299);
    expect(fn).not.toHaveBeenCalled();
    vi.advanceTimersByTime(1);
    expect(fn).toHaveBeenCalledExactlyOnceWith("ab");
  });

  it("drops the pending call on cancel", () => {
    const fn = vi.fn();
    const debounced = debounce(fn, 300);
    debounced("a");
    debounced.cancel();
    vi.advanceTimersByTime(1000);
    expect(fn).not.toHaveBeenCalled();
  });
});
