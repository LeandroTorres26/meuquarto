import { describe, expect, it } from "vitest";
import { RASTER_SCALE, scalePx } from "./box";

describe("scalePx", () => {
  it("multiplies every px length and leaves angles, percentages and colors alone", () => {
    const k = RASTER_SCALE;
    expect(scalePx("translate3d(10px,-20.5px,0px)")).toBe(
      `translate3d(${10 * k}px,${-20.5 * k}px,0px)`,
    );
    expect(scalePx("rotateY(90deg) translateZ(6px)")).toBe(`rotateY(90deg) translateZ(${6 * k}px)`);
    expect(scalePx("3px solid hsl(12 76% 61%)")).toBe(`${3 * k}px solid hsl(12 76% 61%)`);
    expect(scalePx("circle at 70% 30%,red 0 6px,transparent 7px")).toBe(
      `circle at 70% 30%,red 0 ${6 * k}px,transparent ${7 * k}px`,
    );
    expect(scalePx("50%")).toBe("50%");
  });
});
