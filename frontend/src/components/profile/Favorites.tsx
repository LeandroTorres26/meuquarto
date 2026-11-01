import type { PublicItem } from "@/lib/profile-view";
import { HighlightCard } from "./HighlightCard";

/** "Favoritos de tudo": mixed media, so every card carries its category sticker. No "Ver todos" here. */
export function Favorites({ items }: { items: PublicItem[] }) {
  return (
    <section aria-labelledby="fav-h">
      <div className="sec-head sec-head--media">
        <h2 id="fav-h" className="t-h2">
          Favoritos de tudo
        </h2>
      </div>
      <ul className="shelf">
        {items.map((item) => (
          <li key={item.ref}>
            <HighlightCard item={item} showTag eager />
          </li>
        ))}
      </ul>
    </section>
  );
}
