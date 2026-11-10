import "server-only";
import { readFile } from "node:fs/promises";
import { join } from "node:path";
import { ImageResponse } from "next/og";
import { siteUrl } from "@/lib/public-profile";
import { nameFontSize } from "@/lib/og-name";

// The share image (Open Graph and Twitter card) drawn with next/og. Text only: no cover and no
// avatar, so the server never downloads an image from a platform or from storage to draw it.

export const SHARE_IMAGE_SIZE = { width: 1200, height: 630 };
export const TAGLINE = "Seu gosto num quarto só";

// next/og takes ttf, otf or woff, not the woff2 variable fonts the site loads, so this one static
// weight lives beside them (see src/fonts/README.md). Read once, when the module loads.
const display = readFile(join(process.cwd(), "src/fonts/BigShouldersDisplay-ExtraBold-static.ttf"));

// The default `noite` theme (docs/design-system/tokens.json) and the fixed symbol colors (Logo.tsx).
const SURFACE = "hsl(225 20% 8%)";
const INK = "hsl(42 32% 92%)";
const BRAND = "hsl(14 100% 62%)";

/** Same room-corner symbol as components/Logo.tsx, in a plain <svg>. */
function Mark() {
  return (
    <svg width="72" height="72" viewBox="0 0 64 64">
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

/**
 * `heading` is a profile's display name or, with no owner, the tagline. `path` is what follows the
 * host in the address line ("" for the default image). Both are rendered as text only.
 */
export async function shareImage(heading: string, path = "") {
  return new ImageResponse(
    <div
      style={{
        width: "100%",
        height: "100%",
        display: "flex",
        flexDirection: "column",
        justifyContent: "space-between",
        padding: 80,
        background: SURFACE,
        color: INK,
        fontFamily: "Big Shoulders Display",
      }}
    >
      <div style={{ display: "flex", alignItems: "center", gap: 20 }}>
        <Mark />
        <div style={{ fontSize: 56, color: BRAND }}>meuquarto</div>
      </div>
      <div style={{ display: "flex", flexDirection: "column", gap: 16 }}>
        <div style={{ fontSize: 40, opacity: 0.7 }}>{`${new URL(siteUrl()).host}${path}`}</div>
        <div
          style={{
            fontSize: nameFontSize(heading),
            lineHeight: 1,
            textTransform: "uppercase",
            lineClamp: 2,
          }}
        >
          {heading}
        </div>
      </div>
    </div>,
    {
      ...SHARE_IMAGE_SIZE,
      fonts: [{ name: "Big Shoulders Display", data: await display, weight: 800, style: "normal" }],
    },
  );
}
