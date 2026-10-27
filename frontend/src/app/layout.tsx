import type { Metadata } from "next";
import { Caveat, Figtree } from "next/font/google";
import localFont from "next/font/local";
import "@/styles/meuquarto.tokens.css";
import "@/styles/components.css";
import "@/styles/site.css";
import "./globals.css";

// Self-hosted by next/font (CSP font-src 'self'). The families are exposed as CSS
// variables and globals.css points the design tokens (--font-display, --font-sans, --font-hand) at them.
// Big Shoulders Display is no longer listed by next/font/google (Google merged it into "Big Shoulders"),
// so the latin subset of the Display variable font (SIL OFL) is committed and self-hosted instead.
const display = localFont({
  src: "../fonts/BigShouldersDisplay-latin-variable.woff2",
  weight: "700 900",
  variable: "--font-display-loaded",
  display: "swap",
});
const sans = Figtree({
  subsets: ["latin"],
  weight: ["400", "500", "600", "700"],
  variable: "--font-sans-loaded",
  display: "swap",
});
const hand = Caveat({
  subsets: ["latin"],
  weight: ["500", "600", "700"],
  variable: "--font-hand-loaded",
  display: "swap",
});

export const metadata: Metadata = {
  title: "meuquarto",
  description: "Seus filmes, séries, animes, mangás, livros, games e música num endereço só.",
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html
      lang="pt-BR"
      data-theme="noite"
      className={`${display.variable} ${sans.variable} ${hand.variable}`}
    >
      <body>{children}</body>
    </html>
  );
}
