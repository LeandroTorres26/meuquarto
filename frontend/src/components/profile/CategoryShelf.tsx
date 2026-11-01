import Link from "next/link";
import {
  CATEGORY_META,
  countLabel,
  seeAllHref,
  showSeeAll,
  type PublicSection,
} from "@/lib/profile-view";
import { HighlightCard } from "./HighlightCard";

/** One category of the profile: heading with the collection count, its highlights, "Ver todos" when there is more. */
export function CategoryShelf({ slug, section }: { slug: string; section: PublicSection }) {
  const meta = CATEGORY_META[section.category];
  const headingId = `cat-${meta.tag}-h`;
  const count = countLabel(section.category, section.total);
  return (
    <section className="shelf-row" aria-labelledby={headingId}>
      <div className="sec-head sec-head--media">
        <div className="pair-2">
          <h2 id={headingId} className="t-h2">
            {meta.heading}
          </h2>
          <p className="t-sm">{section.mostRecent ? `${count} · mais recentes` : count}</p>
        </div>
        {showSeeAll(section) ? (
          <Link href={seeAllHref(slug, section.category)} className="t-label">
            Ver todos<span className="sr"> ({count})</span>
          </Link>
        ) : null}
      </div>
      <ul className="shelf shelf--scroll">
        {section.items.map((item) => (
          <li key={item.ref}>
            <HighlightCard item={item} />
          </li>
        ))}
      </ul>
    </section>
  );
}
