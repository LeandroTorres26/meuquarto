import Link from "next/link";
import { listingHref, type ListingState } from "@/lib/category-listing";
import type { Category } from "@/lib/profile-view";

/** Previous / next page links of the listing (S18). Plain links, so every page has its own address. */
export function ListingPager({
  slug,
  category,
  state,
  pages,
}: {
  slug: string;
  category: Category;
  state: ListingState;
  pages: number;
}) {
  if (pages <= 1) return null;
  return (
    <nav className="actions" aria-label="Páginas da coleção">
      {state.page > 1 ? (
        <Link
          className="mq-btn mq-btn--secondary"
          rel="prev"
          href={listingHref(slug, category, { ...state, page: state.page - 1 })}
        >
          Página anterior
        </Link>
      ) : null}
      <p className="t-sm">
        Página {state.page} de {pages}
      </p>
      {state.page < pages ? (
        <Link
          className="mq-btn mq-btn--secondary"
          rel="next"
          href={listingHref(slug, category, { ...state, page: state.page + 1 })}
        >
          Próxima página
        </Link>
      ) : null}
    </nav>
  );
}
