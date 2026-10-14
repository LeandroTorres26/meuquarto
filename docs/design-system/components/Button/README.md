Button is the single action control, in four variants: `primary` (the one main action of a view), `secondary`, `ghost` (low-emphasis actions such as "Desconectar") and `danger` (destructive, always with a word that names the action).

React: `<Meuquarto.Button variant size icon iconOnly loading disabled href onClick>Label</Meuquarto.Button>`. Plain HTML: `button.mq-btn.mq-btn--<variant>`, add `mq-btn--sm`, `mq-btn--icon`, `is-loading` plus a `span.mq-spinner`.

| State | Look |
| --- | --- |
| Default | `md` keeps a 44px touch target; `sm` is 36px, for rows and cards. |
| Hover | primary darkens 8%; secondary fills `surface-300`; ghost fills `brand-tint`; danger fills `danger` with `surface-100` text. |
| Focus | 2px `focus` ring, 2px offset. |
| Disabled | flat `surface-300` fill and `ink-muted` label, not opacity, so it stays readable. When a wait applies, the label says it ("Aguarde 15 min"). |
| Loading | same width, label hidden, spinner in the label color, `aria-busy`, clicks blocked. The spinner slows down with reduced motion. |

Copy: short imperative pt-BR in sentence case, naming exactly what happens ("Salvar perfil", "Conectar Steam"). The toast that follows uses the same verb ("Salvo.").

Do: one primary per view; icon-only buttons always get `aria-label`. Do not: use `accent` as a fill, wrap the label (labels never break), or signal danger by color alone.
