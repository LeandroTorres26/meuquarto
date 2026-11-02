import { fitToArea, type Fit } from "./camera";

export type LayoutElements = {
  room: HTMLElement | null;
  bar: HTMLElement | null;
  tools: HTMLElement | null;
  sheet: HTMLElement | null;
  expand: HTMLElement | null;
};
export type LayoutFlags = { collapsed: boolean; side: boolean; touch: boolean };

/**
 * Measures the free area of the screen and fits the room into it: below the top bar, and above the
 * sheet and the tools (bottom layout) or left of the side panel. It also publishes the sheet's
 * height as `--sheet-h` (the tools and the hint sit above it). Null while the elements do not exist.
 */
export function measureRoomLayout(els: LayoutElements, flags: LayoutFlags): Fit | null {
  const { room, bar, tools, sheet, expand } = els;
  if (!room || !bar || !tools || !sheet) return null;
  const { collapsed, side, touch } = flags;
  const sheetBox = sheet.getBoundingClientRect();
  room.style.setProperty(
    "--sheet-h",
    side || collapsed ? "0px" : `${room.getBoundingClientRect().bottom - sheetBox.top}px`,
  );
  const barBottom = bar.getBoundingClientRect().bottom;
  const toolsBox = tools.getBoundingClientRect();
  // a folded panel is one small button: that button's top replaces the sheet's
  const panelLeft = collapsed ? window.innerWidth + 8 : sheetBox.left;
  const panelTop = collapsed
    ? (expand?.getBoundingClientRect().top ?? window.innerHeight)
    : sheetBox.top;
  const bottom =
    side || touch ? (side ? toolsBox.top : panelTop) : Math.min(panelTop, toolsBox.top);
  return fitToArea({
    x0: 8,
    x1: side ? panelLeft - 16 : window.innerWidth - 8,
    y0: (touch && !side ? toolsBox.bottom : barBottom) + 8,
    y1: bottom - 12,
  });
}

/** The part of the screen where a selected object is framed on touch. */
export function visibleArea(
  tools: HTMLElement,
  sheet: HTMLElement,
  side: boolean,
): { x0: number; x1: number; y0: number; y1: number } {
  const sheetBox = sheet.getBoundingClientRect();
  const toolsBox = tools.getBoundingClientRect();
  return {
    x0: 8,
    x1: side ? sheetBox.left - 16 : window.innerWidth - 8,
    y0: side ? 72 : toolsBox.bottom + 8,
    y1: (side ? toolsBox.top : sheetBox.top) - 12,
  };
}
