import type { RoomObject } from "./objects/RoomObject";

/**
 * The labels: one real <button> per object in a flat layer above the 3D tree, so walls never cover
 * them. Hidden labels let the pointer through but stay in the Tab order; focus shows the label and
 * the glow like hover does. On touch each has a small breathing dot inside a 44px target.
 */
export function LabelLayer({
  objects,
  shown,
  selected,
  registerPin,
  onPick,
  onFocusChange,
}: {
  objects: readonly RoomObject[];
  /** Ids whose label is visible (selected, hovered, focused, or all during the first-visit hint). */
  shown: ReadonlySet<string>;
  selected: string | null;
  registerPin: (id: string, el: HTMLElement | null) => void;
  onPick: (id: string) => void;
  onFocusChange: (id: string | null) => void;
}) {
  return (
    <div>
      {objects.map((object) => (
        <div key={object.id} className="pin-anchor" ref={(el) => registerPin(object.id, el)}>
          <button
            type="button"
            className={shown.has(object.id) ? "room-pin is-shown" : "room-pin"}
            data-k={object.id}
            aria-pressed={selected === object.id}
            onClick={() => onPick(object.id)}
            onFocus={() => onFocusChange(object.id)}
            onBlur={() => onFocusChange(null)}
          >
            <span className="room-pin__label">{object.label}</span>
            <span className="room-pin__dot" aria-hidden="true" />
          </button>
        </div>
      ))}
    </div>
  );
}
