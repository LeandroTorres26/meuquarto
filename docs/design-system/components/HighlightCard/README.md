HighlightCard shows one pinned work as the physical object of its medium (poster, volume, record sleeve with disc, game case, DVD), with a clean cover and the title and year always below it.

## Data contract

React: `<Meuquarto.HighlightCard id category title year volume cover note href fix showTag loading error disabled />`. Plain HTML works too: reuse the class names and set `--tilt-f` yourself (see Rotation).

| Prop | Type | Rule |
| --- | --- | --- |
| `id` | string | Required. Stable id of the work (`media_items.id`). Drives the tilt and the fixing. |
| `category` | `movie` `series` `anime` `manga` `book` `game` `music` | Picks the shape and the sticker. |
| `title` | string | Shown below the cover in 2 lines at most, then an ellipsis. Full text goes in `title` and in the link's `aria-label`. |
| `year`, `volume` | string, number | One line below the title: `2020 · vol. 3`. Truncated with an ellipsis. Never printed on the cover. |
| `cover` | url | Hotlinked image. Leave empty for the "no cover" state. |
| `note` | string | Handwritten slip, one line, about 28 characters. |
| `fix` | `tape` `pin` `none` | Defaults to a value derived from `id`. |

## Rules

- Nothing printed or typeset sits on the cover. The only things allowed on it are decorative: category sticker (top left), tape or tack (top right), handwritten slip (lower right). The console band of the game case and the spine of the DVD sit beside the art, not over it, and carry no text.
- Titles: Big Shoulders uppercase, `line-clamp: 2`, `overflow-wrap: anywhere`, so long titles and unbroken words never push the layout. Year and volume stay on one truncated line.
- Legibility on any art: sticker, slip and tack have a thin dark ring plus a drop shadow, and the tape has a hairline dark edge and shadow, so they read on white covers and on black covers alike. Do not remove the rings.

## Rotation

The angle is deterministic: it comes from the work's id, never from `Math.random()` and never from the render order. `Meuquarto.tiltFactor(id)` hashes the id with FNV-1a (32 bit) and returns a factor `f`, set as `--tilt-f`. The angle is `f * tilt-max`.

- Range: magnitude from `tilt-min` (0.9°) to `tilt-max` (2.5°), either direction. No object is ever flat and none exceeds 2.5°.
- The same work has the same angle everywhere (grid, search, room list), on server and client. Reimplement the function exactly in server code:

```js
function hash(id){let x=2166136261,s=String(id);for(let i=0;i<s.length;i++){x^=s.charCodeAt(i);x=Math.imul(x,16777619)>>>0}return x>>>0}
function tiltFactor(id){const x=hash(id),u=(x%1000)/999,mag=0.9/2.5+(1-0.9/2.5)*u;return Math.round(((x>>>10)&1?1:-1)*mag*1000)/1000}
function fixKind(id){return (hash(id)>>>11)&1?'tape':'pin'}
```

- Only the cover tilts; the title stays straight. The tape angle and the slip angle follow the same factor.
- In a `mq-grid` narrower than 480px the angle is multiplied by `tilt-mobile-scale` (0.6), giving 0.5° to 1.5°. Rotations shrink, they do not disappear.

## Variants by medium

| `category` | Shape class | Front ratio | Notes |
| --- | --- | --- | --- |
| `movie`, `series` | `mq-item--poster` | 2:3 | flat print |
| `book` | `mq-item--book mq-item--volume` | 2:3 | spine shade left, page block right |
| `manga` | `mq-item--manga mq-item--volume` | 5:7.4 | volume number goes in the subline |
| `music` | `mq-item--vinyl` | sleeve 1:1, disc to the right | disc is part of the object, not content |
| `game` | `mq-item--game` | 5:7 | black console band above the art |
| `anime` | `mq-item--dvd` | 5:7 | black spine on the left, plastic sheen |

## States

| State | How | Look |
| --- | --- | --- |
| Normal | default | tilted, `shadow-poster` |
| Hover | pointer over the item (`is-hover` to document) | straightens and lifts 4px, `shadow-lift`; no motion with `prefers-reduced-motion` |
| Focus | `:focus-visible` on the link (`state="focus"` to document) | 2px `focus` ring, 6px offset |
| Disabled | `disabled` | 50% opacity, grayscale, not a link, no hover, no decorations |
| Loading | `loading` | shimmering cover slot at the real ratio, two title bars, no tilt, no decorations, `aria-busy` |
| Error | `error` | cover slot with `image-off` icon in `danger` and "Capa indisponível" |
| Empty (no cover) | no `cover` | hatched `surface-300` slot with the category icon, title below as usual |
| Empty shelf slot | `Meuquarto.HighlightSlot` | dashed `border-strong` slot with "Fixar obra" at the same ratio |

Do not put a placeholder wider or taller than the real cover, and do not print the title on a stand-in cover.
