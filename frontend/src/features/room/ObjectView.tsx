import { memo, useMemo, type CSSProperties } from "react";
import { boxTransform, faceGeometry, type Box } from "./box";
import type { RoomObject } from "./objects/RoomObject";

function faceStyle(
  box: Box,
  lamp: boolean,
  face: ReturnType<typeof faceGeometry>[number],
): CSSProperties {
  const texture = box.textures?.[face.face];
  return {
    left: face.left,
    top: face.top,
    width: face.width,
    height: face.height,
    transform: face.transform,
    background: texture?.background ?? box.color,
    border: texture?.border || undefined,
    borderRadius: texture?.borderRadius,
    boxShadow:
      face.shade > 0 ? `inset 0 0 0 9999px hsl(240 33% 5% / ${face.shade.toFixed(2)})` : undefined,
  };
}

function BoxView({ box, lamp, lit }: { box: Box; lamp: boolean; lit: boolean }) {
  return (
    <div
      className={lit ? "bx is-lit" : "bx"}
      data-g={box.group || undefined}
      style={{ transform: boxTransform(box) }}
    >
      {faceGeometry(box, lamp).map((face) => {
        const image = box.textures?.[face.face]?.image;
        return (
          <div key={face.face} className="fc" style={faceStyle(box, lamp, face)}>
            {image ? (
              // eslint-disable-next-line @next/next/no-img-element -- hotlinked provider cover (ADR 0006), https only
              <img src={image} alt="" draggable={false} referrerPolicy="no-referrer" />
            ) : null}
          </div>
        );
      })}
    </div>
  );
}

/**
 * One object of the room as its boxes. It asks the object for them (`boxes(view)`) and never
 * checks which kind of object it is: the same component draws the wall, the shelf and the TV.
 * Memoized, so hovering one object re-renders only that one.
 */
export const ObjectView = memo(function ObjectView({
  object,
  lamp,
  revealed,
  lit,
}: {
  object: RoomObject;
  lamp: boolean;
  /** This object is showing its "pulled out" pose. */
  revealed: boolean;
  lit: boolean;
}) {
  const boxes = useMemo(
    () => object.boxes({ lamp, revealed: revealed ? object.id : null }),
    [object, lamp, revealed],
  );
  return (
    <>
      {boxes.map((box, index) => (
        <BoxView key={index} box={box} lamp={lamp} lit={lit} />
      ))}
    </>
  );
});
