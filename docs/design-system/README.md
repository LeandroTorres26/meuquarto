meuquarto is a profile that works like the owner's room: posters taped to the wall, volumes on the shelf, a record leaning out of its sleeve. The artwork of the works is the star; the interface stays out of its way. Default theme is dark (`noite`), light (`dia`) is an option, and profile themes are sold as premium items.

## Content fundamentals

- UI copy in pt-BR, informal "você", short sentences, sentence case. Code, repo and technical docs stay in English.
- Write the name as `meuquarto`: one word, lowercase in running text. As a wordmark it is set in `display-xl`, uppercase.
- Feature names as the product uses them: "Agora", "Favoritos de tudo", "Ver quarto", "Sincronizar agora". CTAs are imperative: "Crie o seu", "Copiar link", "Ver loja".
- Personal notes sound like a person scribbling on a slip: lowercase, a few words ("melhor final", "repeat o mês todo"). Never longer than one line.
- No emoji, no exclamation marks, no urgency tricks in the store.

## Visual foundations

- The wall is `surface-100`; shelves and panels are `surface-200`; `ink` is the text. `brand` is the action color and `accent` is the lamp (fill only, never text). Pair every fill with its `on-` token.
- Two voices, never mixed in one role. **Identity** is Big Shoulders Display, uppercase, like a poster: `display-xl` (landing hero), `display` (profile name, large wordmark) and `poster` (titles printed under covers, the Agora item, sticker labels). **Interface** is Figtree, sentence case: `h1` page title, `h2` section, `h3` group or card, `lead`, `body`, `body-sm`, `label`, `caption`. A section title is never set in the display face; a cover title is never set in Figtree. Personal notes and the photo caption are Caveat (`note`, `note-lg`), never below 20px, only on `paper` in `paper-ink` or as the single pinned tagline in `brand`.
- Hierarchy per screen: one `h1` (or one `display` on the profile), then `h2` sections, then `h3` groups. Do not skip a level to get a size; do not add a small label above a title to explain it. Meta text sits below its title in `caption` or `body-sm`, not glued with middle dots: use separate elements or a short sentence.
- Spacing follows proximity levels (tokens.json `spacing`): L1 4 to 8 inside an element, L2 16 to 24 inside a group, L3 32 between groups of a section, L4 48 (mobile) or 64 between sections, L5 96 at the page end. Each level is about twice the one inside it; never let the space inside a group equal the space around it. A title is always closer to its own content than to the previous section.
- Common region (a panel or card) means "these belong together and are separate from the rest". Use it for repeated objects (an account, a store item, a list row you can drag) and for the one thing that needs focus (a dialog, a warning). Do not box a section that a heading and space already group.
- Alignment: every page sits on one 12-column grid (8 on tablet, 4 on mobile) with the container's gutter. Left-align text; center only short system messages. Media in different sections of the same page share the same column width so edges line up.
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

Every color is written as `hsl(H S% L%)` (alpha: `hsl(H S% L% / a)`), so a hue family is easy to read and shift. Build shades, hovers and gradients from a token with relative color syntax rather than a new raw value:

```css
.btn:hover   { background: hsl(from var(--brand) h s calc(l - 8)); }
.glow        { background: linear-gradient(hsl(from var(--accent) h s l / .35), transparent); }
.tint        { background: hsl(from var(--brand) h calc(s * .4) 16%); }
```

- Theme names must contain the word "dark" or "night" when the theme is dark: the design system viewer uses the name to choose a dark or light stage behind the previews. That is why the names read `Noite · dark`, `Dia · light`, `Locadora · dark`, `Estufa · dark`.
| Theme | id | Access | Notes |
| --- | --- | --- | --- |
| Noite | `noite` | free, default | dark; fallback for any token a theme leaves out |
| Dia | `dia` | free | light, same structure |
| Locadora | `locadora` | premium (purchase) | dark |
| Estufa | `estufa` | premium (purchase) | dark |

BR-05: a premium theme can only be applied by an account that bought it; purchases are permanent. Free themes are always selectable.

- `locadora` and `estufa` are the premium examples. A premium theme may change every color token, including the seven `sticker-*` colors, but must keep the contrast pairs named in each token's usage note (text 4.5:1, borders and focus 3:1). It never uses franchise art, logos or characters; backgrounds and frames are original.

## Iconography

Icons are Lucide (ISC license), 24px grid, 2px stroke, `currentColor`, shown at 14px inside stickers and 18px inside buttons. Inline the SVG with `class="mq-icon"`. Category icons: Filme `clapperboard`, Série `tv`, Anime `sparkles`, Mangá `book-copy`, Livro `book-open`, Música `disc-3`, Game `gamepad-2`. Action icons: `copy`, `door-open`, `share-2`.

## Marks

No logo exists yet. Set the wordmark `meuquarto` in `display-xl`, uppercase, in `ink`. Do not draw a symbol. Provider attribution (TMDB and others) goes in the profile footer in `caption` and `ink-muted`.

## Components

HighlightCard, HighlightGrid, CategoryTag and ProfileHeader carry the personality. Button, NowCard, Toast and LimitUpsell are the sober functional set (no tilt, tape or handwriting) and document every state: default, hover, focus, disabled and loading for Button; default, pinned, error, empty and loading for NowCard; success and error for Toast. HighlightCard, HighlightSlot, HighlightGrid, CategoryTag, Button, NowCard, Toast, LimitUpsell and Icon are implemented in `components/bundle.js` (`window.Meuquarto`, React 18); props are typed in `components/index.d.ts`. ProfileHeader is markup only (`mq-profile` classes). Include `tokens.css`, then `components/bundle.css`, then React and `bundle.js`; load the three Google Fonts (Big Shoulders Display 700 to 900, Figtree 400 to 700, Caveat 500 to 700) and set the theme on `html`.
