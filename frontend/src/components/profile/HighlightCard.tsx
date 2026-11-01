import { Star } from "lucide-react";
import { itemClasses, itemLabel, itemStyle, type PublicItem } from "@/lib/profile-view";
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
  highlighted = false,
  href,
}: {
  item: PublicItem;
  showTag?: boolean;
  eager?: boolean;
  /** False in the panel (S7): the card is only a preview, so the box is not a link. */
  interactive?: boolean;
  /** S18: the owner pinned this work as a highlight ("Destaque" seal). */
  highlighted?: boolean;
  /** Where the link goes without JavaScript; defaults to `?item={ref}` on the current page. */
  href?: string;
}) {
  const body = (
    <>
      <ItemCover item={item} showTag={showTag} showSlip eager={eager} />
      <div className="mq-item__meta">
        <h3 className="mq-item__title">{item.title}</h3>
        {item.year ? <p className="mq-item__sub">{item.year}</p> : null}
        {highlighted ? (
          <span className="hl-seal">
            <Star className="icon" aria-hidden="true" />
            Destaque
          </span>
        ) : null}
      </div>
    </>
  );
  return (
    <article className={itemClasses(item)} style={itemStyle(item)}>
      {interactive ? (
        <a
          className="mq-item__link"
          href={href ?? `?item=${item.ref}`}
          data-item-ref={item.ref}
          data-item-category={item.category}
          aria-label={highlighted ? `${itemLabel(item)}, destaque` : itemLabel(item)}
        >
          {body}
        </a>
      ) : (
        <div className="mq-item__link">{body}</div>
      )}
    </article>
  );
}
