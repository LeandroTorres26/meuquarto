import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "meuquarto",
  description: "Seus filmes, séries, animes, mangás, livros, games e música num endereço só.",
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html lang="pt-BR">
      <body>{children}</body>
    </html>
  );
}
