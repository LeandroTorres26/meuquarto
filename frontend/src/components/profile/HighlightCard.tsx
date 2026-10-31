import type { CSSProperties } from "react";
import {
  CATEGORY_META,
  itemLabel,
  shapeClasses,
  tiltFactor,
  type PublicItem,
} from "@/lib/profile-view";
import { ItemCover } from "./ItemCover";

/**
 * One highlighted work as the physical object of its medium. The link is `?item={ref}`: ItemDialog
 * intercepts the click and opens S17 over the profile; without JavaScript it is an ordinary link to
 * the same page with the dialog open. `data-*` give the dialog the medium before the item loads.
 */
export function HighlightCard({
  item,
  showTag = false,
  eager = false,
  interactive = true,
}: {
  item: PublicItem;
  showTag?: boolean;
  eager?: boolean;
  /** False in the panel (S7): the card is only a preview, so the box is not a link. */
  interactive?: boolean;
}) {
  const meta = CATEGORY_META[item.category];
  const body = (
    <>
      <ItemCover item={item} showTag={showTag} showSlip eager={eager} />
      <div className="mq-item__meta">
        <h3 className="mq-item__title">{item.title}</h3>
        {item.year ? <p className="mq-item__sub">{item.year}</p> : null}
      </div>
    </>
  );
  return (
    <article
      className={`mq-item ${shapeClasses(meta.shape)}`}
      style={{ "--tilt-f": tiltFactor(item.ref) } as CSSProperties}
    >
      {interactive ? (
        <a
          className="mq-item__link"
          href={`?item=${item.ref}`}
          data-item-ref={item.ref}
          data-item-category={item.category}
          aria-label={itemLabel(item)}
        >
          {body}
        </a>
      ) : (
        <div className="mq-item__link">{body}</div>
      )}
    </article>
  );
}
