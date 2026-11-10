# Fonts

Self-hosted variable fonts (latin subset, which covers Portuguese), loaded by
`src/app/layout.tsx` with `next/font/local`. All are licensed under the SIL Open Font
License 1.1 (https://openfontlicense.org). Builds must not depend on Google Fonts.

| File                                       | Family / weights                   | Source                                                                                                          | Date             |
| ------------------------------------------ | ---------------------------------- | --------------------------------------------------------------------------------------------------------------- | ---------------- |
| `BigShouldersDisplay-latin-variable.woff2` | Big Shoulders Display 700-900      | Google Fonts (fonts.gstatic.com), latin subset                                                                  | 2026-10 (PR #81) |
| `BigShouldersDisplay-ExtraBold-static.ttf` | Big Shoulders Display 800 (static) | https://fonts.gstatic.com/s/bigshouldersdisplay/v24/fC1MPZJEZG-e9gHhdI4-NBbfd2ys3SjJCx12wPgf9g-_3F0YdQ88JF4.ttf | 2026-11-10       |
| `Figtree-latin-variable.woff2`             | Figtree 400-700                    | https://fonts.gstatic.com/s/figtree/v9/_Xms-HUzqDCFdgfMm4S9DQ.woff2                                             | 2026-10-31       |
| `Caveat-latin-variable.woff2`              | Caveat 500-700                     | https://fonts.gstatic.com/s/caveat/v23/Wnz6HAc5bAfYB2Q7ZjYY.woff2                                               | 2026-10-31       |

The woff2 files come from the Google Fonts css2 API (`family=Figtree:wght@400..700`,
`family=Caveat:wght@500..700`), `/* latin */` block.

`BigShouldersDisplay-ExtraBold-static.ttf` is not loaded by the site: it is only for the share image
(`src/lib/og-image.tsx`), because `next/og` reads ttf, otf and woff but not woff2 or variable fonts. It
came from the Google Fonts css2 API (`family=Big+Shoulders+Display:wght@800`, no `text=` subset).
