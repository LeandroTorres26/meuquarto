meuquarto is a profile that works like the owner's room: posters taped to the wall, volumes on the shelf, a record leaning out of its sleeve. The artwork of the works is the star; the interface stays out of its way. Default theme is dark (`noite`), light (`dia`) is an option, and profile themes are sold as premium items.

## Content fundamentals

- UI copy in pt-BR, informal "você", short sentences, sentence case. Code, repo and technical docs stay in English.
- Write the name as `meuquarto`: one word, lowercase in running text. As a wordmark it is set in `display-xl`, uppercase.
- Feature names as the product uses them: "Agora", "Favoritos de tudo", "Ver quarto", "Sincronizar agora". CTAs are imperative: "Crie o seu", "Copiar link", "Ver loja".
- Personal notes sound like a person scribbling on a slip: lowercase, a few words ("melhor final", "repeat o mês todo"). Never longer than one line.
- No emoji, no exclamation marks, no urgency tricks in the store.

## Visual foundations

- The wall is `surface-100`; shelves and panels are `surface-200`; `ink` is the text. `brand` is the action color and `accent` is the lamp (fill only, never text). Pair every fill with its `on-` token.
- Titles are Big Shoulders Display (`display-xl`, `display-lg`, `heading`, `title`), always uppercase, like a poster. Everything else is Figtree (`body`, `body-sm`, `label`, `caption`). Personal notes and the photo caption are Caveat (`note`, `note-lg`), never below 20px, only on `paper` in `paper-ink` or as the single pinned tagline in `brand`.
- Art leads. A media card is the cover itself, full bleed and clean. Title and year always sit below it, never on it (title in 2 lines at most, then an ellipsis). The only things on a cover are decorative: sticker, tape or tack, handwritten slip. While an image loads, show the cover slot at the real aspect ratio.
- Shapes are paper-like: `radius-sm` (2px) on media, stickers and slips; `radius-md` on buttons and inputs; `radius-lg` on panels. Do not use pills for labels or chips. `radius-pill` is only for round things (the tack, the avatar ring).
- Things are stuck to the wall: use `tape` strips or the tack (`mq-pin`) on top-right of a card, a resting angle derived from the work's id (0.9deg to 2.5deg either way, 60% of that below 480px, see HighlightCard), and `shadow-poster`. Hover straightens the cover and lifts it 4px. Respect `prefers-reduced-motion`.
- Category stickers (`mq-tag`) sit on the top-left corner of each item and overlap its edge. Tape and tack stay on the right so they never cover the sticker.
- Functional controls are sober: Button, forms, Toast and LimitUpsell use `surface-*`, `brand` and `border-strong`, with no tilt, tape or handwriting.
- Focus: 2px solid `focus` ring with 2px offset on every interactive element.
- Status: `success` and `danger` are close in lightness, so always add a word or icon.

## Media shapes (shared with the isometric room)

Each medium keeps one physical shape in the grid and in the room scene. Ratios are width:height of the front face; depth is for the 3D scene.

| Medium | Class | Front ratio | Depth | Details |
| --- | --- | --- | --- | --- |
| Filme, Série | `mq-item--poster` | 2:3 | flat | taped or tacked to the wall |
| Livro | `mq-item--book` + `mq-item--volume` | 2:3 | 0.18 | spine shade left, page block right |
| Mangá | `mq-item--manga` + `mq-item--volume` | 5:7.4 | 0.10 | same as book; the volume number goes in the subline |
| Música | `mq-item--vinyl` | sleeve 1:1, record 0.92 of the sleeve | 0.02 | record (`mq-record`) slides out to the right |
| Game | `mq-item--game` | 5:7 | 0.12 | black console band (`mq-gamebar`) above the art, no text |
| Anime | `mq-item--dvd` | 5:7 | 0.12 | plain black spine on the left (`mq-spine`), plastic sheen |

## Themes

`tokens.json` holds one value per theme for every color token; `assets/Code/meuquarto.tokens.css` exports all tokens as CSS variables. The first theme (`noite`) is the default and the fallback for any token a theme leaves out. Apply a theme with `data-theme="<id>"` on `html` or on any wrapper, so a preview of another profile can sit inside the page.

- Theme names must contain the word "dark" or "night" when the theme is dark: the design system viewer uses the name to choose a dark or light stage behind the previews. That is why the names read `Noite · dark`, `Dia · light`, `Locadora · dark`, `Estufa · dark`.
- `noite`: default, dark.
- `dia`: light option, same structure.
- `locadora` and `estufa`: premium examples. A premium theme may change every color token, including the seven `sticker-*` colors, but must keep the contrast pairs named in each token's usage note (text 4.5:1, borders and focus 3:1). It never uses franchise art, logos or characters; backgrounds and frames are original.

## Iconography

Icons are Lucide (ISC license), 24px grid, 2px stroke, `currentColor`, shown at 14px inside stickers and 18px inside buttons. Inline the SVG with `class="mq-icon"`. Category icons: Filme `clapperboard`, Série `tv`, Anime `sparkles`, Mangá `book-copy`, Livro `book-open`, Música `disc-3`, Game `gamepad-2`. Action icons: `copy`, `door-open`, `share-2`.

## Marks

No logo exists yet. Set the wordmark `meuquarto` in `display-xl`, uppercase, in `ink`. Do not draw a symbol. Provider attribution (TMDB and others) goes in the profile footer in `caption` and `ink-muted`.

## Components

HighlightCard, HighlightGrid, CategoryTag and ProfileHeader carry the personality and follow this direction; HighlightCard, HighlightGrid and CategoryTag are implemented in `components/bundle.js` (`window.Meuquarto`, React 18). Button, NowCard, Toast and LimitUpsell inherit the new tokens but are awaiting a redesign. Include `tokens.css`, then `components/bundle.css`, then React and `bundle.js`; load the three Google Fonts (Big Shoulders Display 700 to 900, Figtree 400 to 700, Caveat 500 to 700) and set the theme on `html`.
