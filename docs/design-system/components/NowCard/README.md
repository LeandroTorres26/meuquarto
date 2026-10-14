NowCard is one entry of the "Agora" block: the lamp, a verb, the work and where it came from.

React: `<Meuquarto.NowCard verb title status cover square lamp card state>`. Plain HTML: `div.mq-now` with `.mq-now__thumb`, `.mq-now__body`, `.mq-now__kicker` (lamp + verb), `.mq-now__title`, `.mq-now__status`.

It is a row, not a box: the list that holds the entries is the group (on the profile, a list with dividers; below 1024px a sideways strip). Use `card` only when one entry stands alone.

| State | Look |
| --- | --- |
| Default | cover, lamp, verb in `caption`, title in `poster` type (one line, ellipsis), status in `body-sm`. |
| Pinned by hand | same, status "Escolhido por você". |
| Error | status in `danger` with an icon and the word "Erro.", plus what happens next. |
| Empty | dashed cover slot, no lamp, "Nada por aqui agora" in `body-sm`. On the public profile, hide empty entries instead. |
| Loading | shimmering cover slot and two skeleton bars, `aria-busy`. |

Verbs: Assistindo (filme, série, anime), Lendo (livro, mangá), Ouvindo (música), Jogando (game). Do not call external providers while rendering; read the cached read model.
