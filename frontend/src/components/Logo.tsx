import Link from "next/link";

type Props = {
  /** Renders a link to this path; without it the lockup is a plain span. */
  href?: string;
  id?: string;
  /**
   * "color" is the fixed four-color symbol. "mono" is the one-color symbol in `ink`: it is the
   * switch point for profiles with a premium theme (#37). Nothing picks it yet.
   */
  variant?: "color" | "mono";
};

/** Room corner symbol, 32px. Decorative: the visible wordmark text is the accessible name. */
function Mark({ variant }: { variant: "color" | "mono" }) {
  if (variant === "mono") {
    return (
      <svg
        className="lockup__mark"
        width="32"
        height="32"
        viewBox="0 0 64 64"
        fill="currentColor"
        style={{ color: "var(--ink)" }}
        aria-hidden="true"
      >
        <g transform="translate(20 26) matrix(1 -0.5 0 1 0 0)">
          <path
            fillRule="evenodd"
            d="M-12,-14 H10.5 V12.5 H-12 Z M-9,-10 h7 v7.5 h-7 Z M0,-10 h7 v7.5 h-7 Z M-9,-0.5 h7 v7.5 h-7 Z M0,-0.5 h7 v7.5 h-7 Z"
          />
          <rect x="-8" y="-9" width="5" height="5.5" />
        </g>
        <polygon points="33.5,6.75 56,18 56,44.5 33.5,33.25" />
        <polygon points="32,36.2 54,47.2 32,58 10,47.2" />
      </svg>
    );
  }
  // Fixed brand colors (docs/design-system README, Marks): they do not follow the theme.
  return (
    <svg className="lockup__mark" width="32" height="32" viewBox="0 0 64 64" aria-hidden="true">
      <polygon points="32,6 32,34 8,46 8,18" fill="hsl(14 100% 62%)" />
      <polygon points="32,6 56,18 56,46 32,34" fill="hsl(14 100% 44%)" />
      <polygon points="32,34 56,46 32,58 8,46" fill="hsl(36 87% 62%)" />
      <g transform="translate(20 26) matrix(1 -0.5 0 1 0 0)">
        <rect x="-9" y="-10" width="7" height="7.5" fill="hsl(36 87% 62%)" />
        <rect x="0" y="-10" width="7" height="7.5" fill="hsl(225 41% 18%)" />
        <rect x="-9" y="-0.5" width="7" height="7.5" fill="hsl(225 41% 18%)" />
        <rect x="0" y="-0.5" width="7" height="7.5" fill="hsl(225 41% 18%)" />
      </g>
    </svg>
  );
}

/** Top bar lockup: 32px symbol before the 24px wordmark (docs/design/telas/site.css, `.lockup`). */
export function Logo({ href, id, variant = "color" }: Props) {
  const content = (
    <>
      <Mark variant={variant} />
      meuquarto
    </>
  );
  if (href) {
    return (
      <Link href={href} id={id} className="wordmark lockup">
        {content}
      </Link>
    );
  }
  return (
    <span id={id} className="wordmark lockup">
      {content}
    </span>
  );
}
