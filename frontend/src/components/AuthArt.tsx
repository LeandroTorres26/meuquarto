import { Gamepad2 } from "lucide-react";
import type { CSSProperties } from "react";

// Static markup of Meuquarto.HighlightCard for the decorative cover shelf of S2 and S3
// (docs/design-system/components/HighlightCard/README.md). The covers are abstract placeholders.

type Cover = {
  kind: "poster" | "vinyl" | "game" | "book";
  tilt: number;
  src: string;
  title: string;
  year: string;
  slip?: string;
  /** Finish variant, set by hand here because the covers have no id: only the book carries one. */
  variant?: "ribbon";
};

const tiltStyle = (tilt: number) => ({ "--tilt-f": tilt }) as CSSProperties;

function ArtCard({ kind, tilt, src, title, year, slip, variant }: Cover) {
  const volume = kind === "book" ? " mq-item--volume" : "";
  const modifier = `mq-item--${kind}${volume}${variant ? ` mq-item--${variant}` : ""}`;
  return (
    <li>
      <article className={`mq-item ${modifier}`} style={tiltStyle(tilt)}>
        <div className="mq-item__link">
          <figure className="mq-item__cover">
            {variant === "ribbon" && <span className="mq-ribbon" aria-hidden="true" />}
            {kind === "vinyl" && <span className="mq-record" />}
            {kind === "game" && (
              <span className="mq-gamebar">
                <Gamepad2 className="mq-icon icon" />
              </span>
            )}
            {/* Decorative SVG from /public: nothing for next/image to optimize. */}
            {/* eslint-disable-next-line @next/next/no-img-element */}
            <img className="mq-art" src={src} alt="" />
            {slip && <p className="mq-slip">{slip}</p>}
          </figure>
          <div className="mq-item__meta">
            <h3 className="mq-item__title">{title}</h3>
            <p className="mq-item__sub">{year}</p>
          </div>
        </div>
      </article>
    </li>
  );
}

export function SignUpArt() {
  return (
    <>
      <ArtCard
        kind="poster"
        tilt={-0.618}
        src="/covers/c02.svg"
        title="Um Sonho de Liberdade"
        year="1994"
        slip="nota 10"
      />
      <ArtCard kind="poster" tilt={0.516} src="/covers/c12.svg" title="Vinland Saga" year="2019" />
      <ArtCard
        kind="vinyl"
        tilt={0.619}
        src="/covers/c06.svg"
        title="Meteora"
        year="Linkin Park, 2003"
      />
    </>
  );
}

export function SignInArt() {
  return (
    <>
      <ArtCard kind="game" tilt={0.685} src="/covers/c01.svg" title="Portal 2" year="2011" />
      <ArtCard
        kind="book"
        tilt={-0.804}
        src="/covers/c09.svg"
        title="O Hobbit"
        year="1937"
        slip="nota 10"
        variant="ribbon"
      />
      <ArtCard kind="poster" tilt={-0.603} src="/covers/c07.svg" title="Breaking Bad" year="2008" />
    </>
  );
}
