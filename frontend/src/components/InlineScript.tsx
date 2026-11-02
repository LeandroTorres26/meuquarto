/**
 * A script that runs while the HTML is parsed, before the first paint (Next.js guide "Preventing flash
 * before hydration"). React never runs scripts it renders in the browser and warns about them, so the
 * browser render marks it as plain text; suppressHydrationWarning accepts that one difference.
 */
export function InlineScript({ html }: { html: string }) {
  return (
    <script
      type={typeof window === "undefined" ? "text/javascript" : "text/plain"}
      suppressHydrationWarning
      dangerouslySetInnerHTML={{ __html: html }}
    />
  );
}
