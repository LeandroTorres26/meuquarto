import { memo, useMemo, type CSSProperties } from "react";
import { RASTER_SCALE, boxTransform, faceGeometry, scalePx, type Box } from "./box";
import type { RoomObject } from "./objects/RoomObject";

function faceStyle(
  box: Box,
  lamp: boolean,
  face: ReturnType<typeof faceGeometry>[number],
): CSSProperties {
  const texture = box.textures?.[face.face];
  return {
    left: face.left * RASTER_SCALE,
    top: face.top * RASTER_SCALE,
    width: face.width * RASTER_SCALE,
    height: face.height * RASTER_SCALE,
    transform: scalePx(face.transform),
    background: scalePx(texture?.background ?? box.color),
    border: texture?.border ? scalePx(texture.border) : undefined,
    borderRadius: texture?.borderRadius ? scalePx(texture.borderRadius) : undefined,
    // The glow of a lit object is `--fc-glow` (site.css): the first shadow of the list, so the shade
    // stays inline and the cover is never run through a filter, which would flatten its raster.
    boxShadow:
      face.shade > 0
        ? `var(--fc-glow,0 0 0 0 transparent),inset 0 0 0 9999px hsl(240 33% 5% / ${face.shade.toFixed(2)})`
        : "var(--fc-glow,0 0 0 0 transparent)",
  };
}

function BoxView({ box, lamp, lit }: { box: Box; lamp: boolean; lit: boolean }) {
  return (
    <div
      className={lit ? "bx is-lit" : "bx"}
      data-g={box.group || undefined}
      style={{ transform: scalePx(boxTransform(box)) }}
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
