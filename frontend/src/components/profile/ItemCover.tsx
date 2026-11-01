import { CATEGORY_META, finish, safeImageUrl, type PublicItem } from "@/lib/profile-view";
import { CategoryIcon } from "./CategoryIcon";
import { CategoryTag } from "./CategoryTag";

type Props = {
  item: Pick<PublicItem, "ref" | "category" | "title" | "imageUrl" | "note">;
  /** Alt text of the cover. Empty in grids, where the link already carries the title. */
  alt?: string;
  /** Eager loading for covers that are above the fold. */
  eager?: boolean;
  /** Show the category sticker (Favoritos de tudo mixes media; the dialog shows it too). */
  showTag?: boolean;
  /** Show the owner's note as a handwritten slip (cards only: the dialog prints it on paper). */
  showSlip?: boolean;
  /** Same-origin cover from our own code (the landing page art). Never user or provider data. */
  localSrc?: string;
};

/**
 * The `mq-item__cover` figure of a HighlightCard: the physical object of the medium (spine, record,
 * console band), the cover or the "no cover" slot, sticker, the ribbon of the book variant and slip. The CSS fixes the
 * ratio of each medium, so the slot never moves when the image arrives. Only https covers render.
 */
export function ItemCover({
  item,
  alt = "",
  eager = false,
  showTag = false,
  showSlip = false,
  localSrc,
}: Props) {
  const meta = CATEGORY_META[item.category];
  const src = localSrc ?? safeImageUrl(item.imageUrl);
  const ribbon = finish(item.ref, item.category)?.kind === "ribbon";
  return (
    <figure className="mq-item__cover">
      {ribbon ? <span className="mq-ribbon" aria-hidden="true" /> : null}
      {meta.shape === "vinyl" ? <span className="mq-record" aria-hidden="true" /> : null}
      {meta.shape === "game" ? (
        <span className="mq-gamebar" aria-hidden="true">
          <CategoryIcon category="GAME" className="mq-icon icon" />
        </span>
      ) : null}
      {src ? (
        // eslint-disable-next-line @next/next/no-img-element -- hotlinked provider covers (ADR 0006), https only
        <img
          className="mq-art"
          src={src}
          alt={alt}
          width={meta.width}
          height={meta.height}
          loading={eager ? "eager" : "lazy"}
          decoding="async"
          referrerPolicy="no-referrer"
        />
      ) : (
        <span
          className="mq-art mq-art--empty"
          role={alt ? "img" : undefined}
          aria-label={alt || undefined}
        >
          <CategoryIcon category={item.category} className="mq-icon icon" />
        </span>
      )}
      {showTag ? <CategoryTag category={item.category} /> : null}
      {showSlip && item.note ? <p className="mq-slip">{item.note}</p> : null}
    </figure>
  );
}
