import { ItemCover } from "@/components/profile/ItemCover";
import { byline, itemClasses, itemStyle, type PublicItem } from "@/lib/profile-view";

// Example highlights for the hero (docs/design/telas/landing.html): works the owner has finished. Same markup as a profile
// HighlightCard but not a link: there is no profile behind it. Covers are the abstract placeholders
// in /public/covers, never real posters.
type Example = Pick<PublicItem, "ref" | "category" | "title" | "artist" | "year" | "note"> & {
  cover: string;
};

const EXAMPLES: Example[] = [
  {
    ref: 1,
    category: "MOVIE",
    title: "Perfect Days",
    artist: null,
    year: 2023,
    note: "assisti 3 vezes",
    cover: "c01",
  },
  {
    ref: 2,
    category: "ANIME",
    title: "Vinland Saga",
    artist: null,
    year: 2019,
    note: null,
    cover: "c07",
  },
  {
    ref: 3,
    category: "BOOK",
    title: "A Guerra dos Tronos",
    artist: null,
    year: 1996,
    note: null,
    cover: "c10",
  },
  {
    ref: 4,
    category: "MUSIC",
    title: "Clube da Esquina",
    artist: "Milton Nascimento",
    year: 1972,
    note: null,
    cover: "c11",
  },
];

export function ExampleShelf() {
  return (
    <ul
      className="shelf span-all d-span-6"
      aria-label="Exemplos de destaques"
      style={{ paddingTop: "var(--space-4)" }}
    >
      {EXAMPLES.map((item) => (
        <li key={item.ref}>
          <article className={itemClasses(item)} style={itemStyle(item)}>
            <div className="mq-item__link">
              <ItemCover
                item={{ ...item, imageUrl: null }}
                localSrc={`/covers/${item.cover}.svg`}
                eager
                showTag
                showSlip
              />
              <div className="mq-item__meta">
                <h3 className="mq-item__title">{item.title}</h3>
                <p className="mq-item__sub">{byline(item.artist, item.year)}</p>
              </div>
            </div>
          </article>
        </li>
      ))}
    </ul>
  );
}
