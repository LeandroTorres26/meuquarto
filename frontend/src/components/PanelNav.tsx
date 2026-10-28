import Link from "next/link";
import { Fragment } from "react";

type Item = { label: string; href?: string };

// Only routes that exist are links; the rest of the design's nav stays visible but disabled until
// its screen is built, so nothing links to a 404.
const GROUPS: Item[][] = [
  [{ label: "Perfil" }, { label: "Destaques" }, { label: "Agora" }, { label: "Contas vinculadas" }],
  [{ label: "Loja" }, { label: "Compras" }, { label: "Conta", href: "/painel/conta" }],
];

/** Panel navigation (docs/design/telas/site.css `.pnav`). `current` is the label of the open screen. */
export function PanelNav({ current }: { current: string }) {
  return (
    <nav className="pnav" aria-label="Painel">
      {GROUPS.map((group, i) => (
        <div className="pnav__group" key={i}>
          {group.map((item) => (
            <Fragment key={item.label}>
              {item.href ? (
                <Link
                  className="tab"
                  href={item.href}
                  aria-current={item.label === current ? "page" : undefined}
                >
                  {item.label}
                </Link>
              ) : (
                <span className="tab tab--soon" aria-disabled="true">
                  {item.label}
                </span>
              )}
            </Fragment>
          ))}
        </div>
      ))}
    </nav>
  );
}
