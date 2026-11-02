import type { Metadata } from "next";
import localFont from "next/font/local";
import "@/styles/meuquarto.tokens.css";
import "@/styles/components.css";
import "@/styles/site.css";
import "./globals.css";
import { InlineScript } from "@/components/InlineScript";
import { DEFAULT_THEME, THEME_BOOT_SCRIPT } from "@/lib/theme";

// Self-hosted by next/font (CSP font-src 'self'). The families are exposed as CSS
// variables and globals.css points the design tokens (--font-display, --font-sans, --font-hand) at them.
// The latin subsets of the variable fonts (SIL OFL) are committed in src/fonts (see its README), so
// builds never fetch from Google Fonts.
const display = localFont({
  src: "../fonts/BigShouldersDisplay-latin-variable.woff2",
  weight: "700 900",
  variable: "--font-display-loaded",
  display: "swap",
});
const sans = localFont({
  src: "../fonts/Figtree-latin-variable.woff2",
  weight: "400 700",
  variable: "--font-sans-loaded",
  display: "swap",
});
const hand = localFont({
  src: "../fonts/Caveat-latin-variable.woff2",
  weight: "500 700",
  variable: "--font-hand-loaded",
  display: "swap",
});

export const metadata: Metadata = {
  title: "meuquarto",
  description: "Seus filmes, séries, animes, mangás, livros, games e música num endereço só.",
};

// The site is prerendered with the default theme; the head script swaps in the user's theme (kept in a
// cookie, src/lib/theme.ts) before the first paint, so <html data-theme> differs from the server's on purpose.
export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html
      lang="pt-BR"
      data-theme={DEFAULT_THEME}
      className={`${display.variable} ${sans.variable} ${hand.variable}`}
      suppressHydrationWarning
    >
      <head>
        <InlineScript html={THEME_BOOT_SCRIPT} />
      </head>
      <body>{children}</body>
    </html>
  );
}
