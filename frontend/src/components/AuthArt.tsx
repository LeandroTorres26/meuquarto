import { Gamepad2 } from "lucide-react";
import type { CSSProperties } from "react";

// Static markup of Meuquarto.HighlightCard for the decorative cover shelf of S2 and S3
// (docs/design-system/components/HighlightCard/README.md). The covers are abstract placeholders.

type Cover = {
  kind: "poster" | "dvd" | "vinyl" | "game" | "book";
  tilt: number;
  src: string;
  title: string;
  year: string;
  slip?: string;
  fix: "pin" | "tape";
};

const tiltStyle = (tilt: number) => ({ "--tilt-f": tilt }) as CSSProperties;

function ArtCard({ kind, tilt, src, title, year, slip, fix }: Cover) {
  const modifier = kind === "book" ? "mq-item--book mq-item--volume" : `mq-item--${kind}`;
  return (
    <li>
      <article className={`mq-item ${modifier}`} style={tiltStyle(tilt)}>
        <div className="mq-item__link">
          <figure className="mq-item__cover">
            {kind === "dvd" && <span className="mq-spine" />}
            {kind === "vinyl" && <span className="mq-record" />}
            {kind === "game" && (
              <span className="mq-gamebar">
                <Gamepad2 className="mq-icon icon" />
              </span>
            )}
            {/* Decorative SVG from /public: nothing for next/image to optimize. */}
            {/* eslint-disable-next-line @next/next/no-img-element */}
            <img className="mq-art" src={src} alt="" />
            <span className={fix === "pin" ? "mq-pin" : "mq-tape"} />
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
        title="Central do Brasil"
        year="1998"
        slip="chorei no final"
        fix="pin"
      />
      <ArtCard
        kind="dvd"
        tilt={0.516}
        src="/covers/c12.svg"
        title="Frieren"
        year="2023"
        fix="pin"
      />
      <ArtCard
        kind="vinyl"
        tilt={0.619}
        src="/covers/c06.svg"
        title="Elis &amp; Tom"
        year="1974"
        fix="pin"
      />
    </>
  );
}

export function SignInArt() {
  return (
    <>
      <ArtCard
        kind="game"
        tilt={0.685}
        src="/covers/c01.svg"
        title="Celeste"
        year="2018"
        fix="tape"
      />
      <ArtCard
        kind="book"
        tilt={-0.804}
        src="/covers/c09.svg"
        title="Grande Sertão"
        year="1956"
        slip="nonada"
        fix="tape"
      />
      <ArtCard
        kind="poster"
        tilt={-0.603}
        src="/covers/c07.svg"
        title="Twin Peaks"
        year="1990"
        fix="pin"
      />
    </>
  );
}
