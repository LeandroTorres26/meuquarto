import { describe, expect, it } from "vitest";
import { nameFontSize } from "./og-name";

describe("nameFontSize", () => {
  it("uses the largest size for short names", () => {
    expect(nameFontSize("Leandro")).toBe(176);
    expect(nameFontSize("a".repeat(14))).toBe(176);
  });

  it("steps down as the name grows", () => {
    expect(nameFontSize("a".repeat(15))).toBe(128);
    expect(nameFontSize("a".repeat(20))).toBe(128);
    expect(nameFontSize("a".repeat(21))).toBe(96);
    expect(nameFontSize("a".repeat(25))).toBe(96);
    expect(nameFontSize("a".repeat(26))).toBe(80);
    expect(nameFontSize("Seu gosto num quarto só")).toBe(96);
  });

  it("ignores surrounding spaces and counts characters, not UTF-16 units", () => {
    expect(nameFontSize(`  ${"a".repeat(14)}  `)).toBe(176);
    expect(nameFontSize("😀".repeat(14))).toBe(176);
  });
});
