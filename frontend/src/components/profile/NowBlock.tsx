import { CATEGORY_META, safeImageUrl, type PublicNowItem } from "@/lib/profile-view";
import { NOW_VERB, nowStatus } from "@/lib/now";
import { CategoryIcon } from "./CategoryIcon";

/**
 * The "Agora" block of S4 (FR-15): one NowCard per entry, in a list with dividers (a sideways strip
 * below 1024px). Each title links to `?item={ref}`, which ItemDialog turns into S17. Entries the
 * owner hid or that have nothing to show are simply not in the list, and the block disappears when
 * none is left, as the NowCard README asks. `serverNow` is passed in so the relative time is
 * computed once, on the server.
 */
export function NowBlock({ items, serverNow }: { items: PublicNowItem[]; serverNow: number }) {
  if (items.length === 0) return null;
  return (
    <aside className="now" aria-labelledby="agora-h">
      <h2 id="agora-h" className="t-h3">
        Agora
      </h2>
      <ul className="list">
        {items.map((item) => {
          const src = safeImageUrl(item.imageUrl);
          const square = CATEGORY_META[item.category].shape === "vinyl";
          return (
            <li className="mq-now" key={item.ref}>
              {src ? (
                // eslint-disable-next-line @next/next/no-img-element -- hotlinked provider covers (ADR 0006), https only
                <img
                  className={square ? "thumb thumb--sq" : "thumb"}
                  src={src}
                  alt=""
                  loading="lazy"
                  decoding="async"
                  referrerPolicy="no-referrer"
                />
              ) : (
                <span className={square ? "thumb thumb--sq" : "thumb"} aria-hidden="true">
                  <CategoryIcon category={item.category} className="mq-icon icon" />
                </span>
              )}
              <div className="grow">
                <p className="mq-now__kicker">
                  <span className="mq-now__lamp" aria-hidden="true" />
                  {NOW_VERB[item.category]}
                </p>
                <p className="mq-now__title">
                  <a
                    className="mq-now__link"
                    href={`?item=${item.ref}`}
                    data-item-ref={item.ref}
                    data-item-category={item.category}
                  >
                    {item.title}
                  </a>
                </p>
                <p className="mq-now__status">{nowStatus(item.source, item.at, serverNow)}</p>
              </div>
            </li>
          );
        })}
      </ul>
    </aside>
  );
}
