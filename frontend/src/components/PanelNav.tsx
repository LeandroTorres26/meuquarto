import Link from "next/link";
import { Fragment } from "react";
import { ThemeSync } from "@/components/ThemeSync";

type Item = { label: string; href: string };

const GROUPS: Item[][] = [
  [
    { label: "Perfil", href: "/painel" },
    { label: "Destaques", href: "/painel/destaques" },
    { label: "Agora", href: "/painel/agora" },
    { label: "Contas vinculadas", href: "/painel/contas" },
  ],
  [{ label: "Conta", href: "/painel/conta" }],
];

/**
 * Panel navigation (docs/design/telas/site.css `.pnav`). `current` is the label of the open screen.
 * Every panel screen has it, so it also brings the site theme in line with the saved one (ThemeSync).
 */
export function PanelNav({ current }: { current: string }) {
  return (
    <nav className="pnav" aria-label="Painel">
      <ThemeSync />
      {GROUPS.map((group, i) => (
        <div className="pnav__group" key={i}>
          {group.map((item) => (
            <Fragment key={item.label}>
              <Link
                className="tab"
                href={item.href}
                aria-current={item.label === current ? "page" : undefined}
              >
                {item.label}
              </Link>
            </Fragment>
          ))}
        </div>
      ))}
    </nav>
  );
}
