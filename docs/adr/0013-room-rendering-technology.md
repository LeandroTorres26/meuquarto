# 0013. Room rendering technology: CSS 3D in React

- Status: Accepted (2026-11-01, by the owner, provisionally: revisit if the device check at the start of #43 fails)
- Date: 2026-11-01

## Context
FR-20 asks for a read-only isometric room (S5, `/{slug}/quarto`) built from the profile highlights. The design phase already produced a working prototype, `docs/design/telas/quarto.html`, and the S5 section of `docs/design/telas/README.md` fixes the behavior: camera limits (yaw -88 to -2 degrees, pitch 22 to 34 degrees with soft limits, zoom 0.6x to 3.5x), mouse and touch input models, one label at a time with a `brand` glow, a side panel or bottom sheet with S17 content, reduced motion, and a scene that cannot be selected or dragged. The issue left the choice open between that CSS 3D prototype and Three.js.

How the prototype works (read from the file, not assumed):
- It is plain DOM, no canvas and no SVG. A `B()` helper builds each solid from up to five `div` faces (`front`, `back`, `left`, `right`, `top`) placed with `translate3d`, `rotateX/Y` and `translateZ`, inside a `preserve-3d` tree under `perspective: 2400px`.
- About 100 boxes (walls, floor, shelf, books, TV, console, vinyl, posters), so a few hundred `div`s. Covers are `<img>` or backgrounds on faces. Shading is an inset `box-shadow` per face.
- The camera is a single `transform` on one wrapper (`scale rotateX rotateY translateY`), eased with `requestAnimationFrame` (0.18 of the gap per frame; it jumps when `prefers-reduced-motion`).
- Labels live in a separate flat layer above the 3D tree and are real `<button>`s positioned from the projected bounds of each object's boxes, so walls never cover them. Hover and focus light an object by toggling a class (`.bx.is-lit`: outline plus `drop-shadow`).
- Objects are grouped (`parede`, `mural`, `estante`, `vitrola`, `tv`), each with a position, a label and its items.

Project constraints that matter: deadline end of November 2026 (P2), one developer, CSP `script-src 'self' 'unsafe-inline'` with `unsafe-eval` only in development and `connect-src 'self'` (ADR 0015), the room is lazy loaded from S4 and must not slow the profile (issue #43), and the owner must be able to explain the code (ADR 0018).

## Spike
Done by reading the prototype and the design docs and by checking package metadata. No code was added to the repo and no throwaway build was kept. Three.js figures are order-of-magnitude values (npm reports `three` at 21 MB unpacked, which says little about what a tree-shaken app ships) and are marked "approx."; they were not measured on this project. The decision does not depend on their exact value.

| Criterion | (a) CSS 3D / DOM in React | (b) Three.js + react-three-fiber |
| --- | --- | --- |
| Added JS | No new dependency. Room code is small TypeScript: camera math, box helper, scene data. | `three` (approx. 150 to 200 kB gzip once tree-shaken), `@react-three/fiber` (approx. 30 to 50 kB gzip with its reconciler; peer-pinned to React `>=19 <19.4`, tied to our React upgrades), usually `@react-three/drei` for controls. Approx. 200 to 300 kB gzip in total, all in the lazy chunk. Three new pinned dependencies to audit (CLAUDE.md). |
| Impact on S4 load | None if the room is a dynamic import. | None if lazy loaded too, but the chunk is large and the first visit to the room pays for it on mobile data. |
| Mobile performance | Compositor friendly (a few hundred layers, one transform per frame). Known weak points: many large `preserve-3d` layers on low-end Android, Safari quirks with `filter` on 3D children. Unverified on real devices. | Draw calls are cheap for about 100 boxes, so frame rate is not the issue; cost is WebGL context creation, texture upload, battery, context loss and iOS memory limits. |
| Touch and gestures | Pointer events on one stage; the prototype already implements pinch, two-finger pan, one-finger yaw, no tilt on touch. Same logic is reused. | Rebuilt: OrbitControls does not match the spec (soft pitch limits, no tilt on touch, framing a selection at 1.6x, Space/Shift pan), so custom controls anyway. |
| Accessibility | Labels are real focusable `<button>`s (Tab reaches every object, focus lights it), the panel and chips are plain DOM, `aria-pressed`, a screen reader description. Reduced motion is CSS plus one flag. | The canvas is opaque to assistive technology. Labels, chips and focus order would be a DOM overlay duplicating the scene state, positioned from 3D projection; the focus ring on a 3D object has to be faked. |
| Fidelity to the prototype | Identical by construction: same classes (`site.css`), tokens and covers as real `<img>`, same label layer. Design changes are copy and adjust. | A re-implementation: materials and lighting to imitate the flat face shading, cover textures through `TextureLoader` (covers are hotlinked, ADR 0006; WebGL textures need CORS-clean images, an `<img>` does not), text labels as DOM anyway. Visible differences are likely and each is rework. |
| Effort before end of Nov 2026 | A port, not an invention: scene data to typed objects, camera and input as hooks, tests for the pure math. Estimate 1 to 1.5 weeks including the S17 panel. | New stack to learn and wire (scene graph, controls, textures, resize, disposal, client-only guards in Next 16 with Cache Components). Estimate 2.5 to 4 weeks for the same behavior, with the risk concentrated at the end. |
| `RoomObject` hierarchy | Maps directly: an object returns the boxes that make it up and its label anchor, and handles select. React renders whatever the list says. | Also maps (an object builds a `THREE.Group`), but then the base class imports Three, which breaks the rule that hierarchies are plain and framework free (ADR 0018). |
| Testability | Camera math, soft pitch, fit-to-area, projection of bounds and the object list are pure functions or plain classes: Vitest with no browser (`npm run test` is pure logic). | The math is testable, but scene behavior needs WebGL, not available in the CI Node setup; mostly untestable in CI without heavy mocks. |
| CSP | Nothing new: transforms and styles only. | WebGL needs no `unsafe-eval` and works with the current policy; blob textures or workers could require `img-src blob:` or `worker-src`, a CSP change to review. |
| Ceiling | A fixed isometric room of boxes is enough for FR-20. Curved shapes, real lighting and shadows are not realistic. | Anything later: round objects, real light, particles. |

## Decision
Render the room with **CSS 3D in React components** (option a), porting `quarto.html` instead of re-implementing it in WebGL.

- No new runtime dependency. The room lives in a dynamically imported client component opened from S4 "Ver quarto", so the profile bundle does not change.
- The scene is data plus plain TypeScript. Camera, projection and input handling are hooks over pure functions; React only renders faces, labels, panel and chips.
- Reuse the prototype's CSS (`site.css` classes, design system tokens, `npm run sync:design`) and the S5 behavior table unchanged, including the non-selectable, non-draggable scene.
- Gate in #43, early: run the ported scene on one mid-range Android phone and one iPhone and note the frame rate in the PR. If it is clearly unusable, open a new ADR that supersedes this one. The `RoomObject` classes and camera math below are renderer independent, so that fallback does not throw them away.

## Consequences
- Zero added bundle weight and no new dependency to audit; the room chunk stays small, so opening it from S4 is quick on mobile data.
- The accessibility requirements of the design README (real buttons, Tab to every object, visible focus, reduced motion, a screen reader description) are met with ordinary DOM instead of a parallel overlay.
- Fidelity to the approved design is exact, and later design changes in `docs/design/telas/` carry over with little rework.
- The work fits the schedule and the owner can explain all of it. Pure logic is covered by Vitest in CI.
- Limits accepted: only box geometry and flat shading; no real lighting or shadows. The "Abajur" effect stays a CSS dimming of faces, as in the prototype.
- Risk accepted: `preserve-3d` performance on low-end phones and Safari quirks are not measured yet; the real-device gate handles it early.
- Cover images keep working through plain `<img>` (hotlinked, ADR 0006) with no CORS or canvas concerns; the CSP is unchanged.

## Alternatives considered
- **Three.js with react-three-fiber (and drei):** best ceiling and GPU rendering, but adds roughly 200 to 300 kB gzip (approx.), three dependencies, rebuilt controls, a DOM overlay to recover accessibility, a re-implemented look and more schedule risk, for a fixed room of boxes that CSS already draws. Rejected for now; it is the right tool if the room later needs real lighting or curved models.
- **SVG or 2D canvas isometric sprites:** light and simple, but the camera rotates (yaw -88 to -2 degrees) and zooms continuously, which sprites cannot do without changing the approved behavior.
- **Plain Three.js without react-three-fiber:** saves about 40 kB but needs imperative lifecycle code beside React and keeps every other cost above.

## Sketch for #43
Folder `frontend/src/features/room/` (a proposal; issue #43 owns the final shape):

```
features/room/
  RoomScene.tsx          client component, lazy loaded from S4; owns selection, hover and focus state
  Stage.tsx              perspective wrapper, applies the camera transform, pointer handling
  BoxView.tsx            renders one Box as its faces (a div per face), receives is-lit
  LabelLayer.tsx         flat layer of <button> labels placed from projected bounds
  RoomPanel.tsx          side panel / bottom sheet, chips, S17 content for ?item=
  useCamera.ts           target and current camera, easing in requestAnimationFrame, reduced motion
  usePointerGestures.ts  mouse (drag, right/middle pan, wheel) and touch (1 finger yaw, 2 fingers pinch/pan)
  camera.ts              pure: LIMITS, softPitch, clamp, fitToArea, projectBounds (Vitest)
  box.ts                 pure: Box type and faces(box) -> face transforms (Vitest)
  objects/
    RoomObject.ts        abstract class (plain TypeScript: no React, no Next, no DOM)
    Poster.ts            wall posters and the mural (one class, two placements)
    Shelf.ts
    RecordPlayer.ts
    Tv.ts
    buildRoom.ts         highlights by category -> RoomObject[]
```

The `RoomObject` hierarchy (ADR 0018, optional):

```ts
export abstract class RoomObject {
  protected constructor(
    readonly id: string, // 'parede', 'mural', 'estante', 'vitrola', 'tv'
    readonly label: string, // 'Pôsteres', 'Mural', ...
    protected readonly items: readonly RoomItem[],
  ) {}

  /** The solids that make this object, in room units. Each subclass places itself. */
  abstract boxes(): Box[];

  /** Where the label and the camera frame sit. Default: the center of its boxes. */
  anchor(): Point3 {
    return centerOf(this.boxes());
  }

  /** What a click or Tab + Enter does. Default selects the object; subclasses may override. */
  onSelect(scene: SceneActions): void {
    scene.select(this.id);
  }
}
```

- `Poster`, `Shelf`, `RecordPlayer` and `Tv` extend it with `super(...)` and implement `boxes()` (the layout of the prototype for that group, using the shapes of the design system "Media shapes" table: posters flat, books 0.18 deep, manga 0.10, vinyl 0.02, games 0.12). `RecordPlayer` overrides `onSelect` to also slide the record out, which is a real polymorphism example; the others keep the default.
- `buildRoom(highlights)` returns `RoomObject[]`. `RoomScene` iterates the list and calls `boxes()`, `anchor()` and `onSelect()` through the base type, never checking the subclass. What an empty category shows is decided in #43 with the S5 copy.
- `RoomObject` imports nothing from React, Next or a 3D library, so a later switch to WebGL only replaces `BoxView` and `Stage`.
- Tests (Vitest, named after the behavior): `softPitch` stays within 22 to 34, `fitToArea` does not depend on yaw, every `Shelf` box stays inside the room, `buildRoom` returns one object per category, `RecordPlayer.onSelect` selects and starts the sleeve animation. Keyboard behavior (Tab reaches every label, Esc clears) goes in the PR's manual checklist unless the owner accepts a DOM testing dev dependency.
- `docs/poo-no-projeto.md` gets a short `RoomObject` section in the same PR as the code (CLAUDE.md).
