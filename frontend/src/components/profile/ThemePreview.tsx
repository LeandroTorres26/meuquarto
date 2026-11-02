import { itemClasses, itemStyle, type PublicItem } from "@/lib/profile-view";
import { ItemCover } from "./ItemCover";

// Sample works with the placeholder covers the design system ships (public/covers). They are our own
// art, never the owner's data, and exist only so the colors of a theme can be judged on real cards.
const SAMPLES: { item: PublicItem; cover: string }[] = [
  {
    item: {
      ref: 1,
      category: "MOVIE",
      title: "Perfect Days",
      year: 2023,
      imageUrl: null,
      note: null,
    },
    cover: "/covers/c01.svg",
  },
  {
    item: {
      ref: 2,
      category: "BOOK",
      title: "Torto Arado",
      year: 2019,
      imageUrl: null,
      note: null,
    },
    cover: "/covers/c10.svg",
  },
  {
    item: { ref: 3, category: "MANGA", title: "Vagabond", year: 1998, imageUrl: null, note: null },
    cover: "/covers/c06.svg",
  },
];

/**
 * A small profile drawn inside a `data-theme` wrapper, so the owner sees a theme before saving it
 * (design system: a theme can be applied to any wrapper). The name and the address are the owner's own
 * fields as typed; the cards are samples.
 */
export function ThemePreview({
  theme,
  name,
  slug,
  bio,
  label,
}: {
  theme: string;
  name: string;
  slug: string;
  bio: string;
  label: string;
}) {
  return (
    <div className="theme-preview profile-themed" data-theme={theme} role="img" aria-label={label}>
      <div aria-hidden="true">
        <p className="mq-profile__slug">meuquarto.com.br/{slug}</p>
        <p className="mq-profile__name">{name}</p>
        {bio ? <p className="mq-profile__bio bio-text">{bio}</p> : null}
        <ul className="shelf" style={{ marginTop: "var(--space-3)" }}>
          {SAMPLES.map(({ item, cover }) => (
            <li key={item.ref}>
              <article className={itemClasses(item)} style={itemStyle(item)}>
                <div className="mq-item__link">
                  <ItemCover item={item} localSrc={cover} />
                  <div className="mq-item__meta">
                    <h3 className="mq-item__title">{item.title}</h3>
                  </div>
                </div>
              </article>
            </li>
          ))}
        </ul>
      </div>
    </div>
  );
}
