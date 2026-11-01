HighlightCard shows one pinned work as the physical object of its medium (poster, volume, record sleeve with disc, game case), with a clean cover and the title and year always below it.

## Data contract

React: `<Meuquarto.HighlightCard id category title year volume cover note href showTag loading error disabled />`. Plain HTML works too: reuse the class names and set `--tilt-f` yourself (see Rotation).

| Prop | Type | Rule |
| --- | --- | --- |
| `id` | string | Required. Stable id of the work (`media_items.id`). Drives the tilt and the finish variant. |
| `category` | `movie` `series` `anime` `manga` `book` `game` `music` | Picks the shape and the sticker. |
| `title` | string | Shown below the cover in 2 lines at most, then an ellipsis. Full text goes in `title` and in the link's `aria-label`. |
| `year`, `volume` | string, number | One line below the title: `2020 · vol. 3`. Truncated with an ellipsis. Never printed on the cover. |
| `cover` | url | Hotlinked image. Leave empty for the "no cover" state. |
| `note` | string | Handwritten slip, one line, about 28 characters. |

## Rules

- Nothing printed or typeset sits on the cover. The only things allowed on it are decorative: category sticker (top left), the medium's finish variant (see Media finish) and the handwritten slip (lower right). Media never show tape or a tack: posters are glued from behind. The console band of the game case sits beside the art, not over it, and carries no text.
- Titles: Big Shoulders uppercase, `line-clamp: 2`, `overflow-wrap: anywhere`, so long titles and unbroken words never push the layout. Year and volume stay on one truncated line.
- Legibility on any art: sticker and slip have a thin dark ring plus a drop shadow, so they read on white covers and on black covers alike. Do not remove the rings.

## Rotation

The angle is deterministic: it comes from the work's id, never from `Math.random()` and never from the render order. `Meuquarto.tiltFactor(id)` hashes the id with FNV-1a (32 bit) and returns a factor `f`, set as `--tilt-f`. The angle is `f * tilt-max`.

- Range: magnitude from `tilt-min` (0.9°) to `tilt-max` (2.5°), either direction. No object is ever flat and none exceeds 2.5°.
- The same work has the same angle everywhere (grid, search, room list), on server and client. Reimplement the function exactly in server code:

```js
function hash(id){let x=2166136261,s=String(id);for(let i=0;i<s.length;i++){x^=s.charCodeAt(i);x=Math.imul(x,16777619)>>>0}return x>>>0}
function tiltFactor(id){const x=hash(id),u=(x%1000)/999,mag=0.9/2.5+(1-0.9/2.5)*u;return Math.round(((x>>>10)&1?1:-1)*mag*1000)/1000}
```

- Only the cover tilts; the title stays straight. The slip angle follows the same factor.
- In a `mq-grid` narrower than 480px the angle is multiplied by `tilt-mobile-scale` (0.6), giving 0.5° to 1.5°. Rotations shrink, they do not disappear.

## Variants by medium

| `category` | Shape class | Front ratio | Notes |
| --- | --- | --- | --- |
| `movie`, `series`, `anime` | `mq-item--poster` | 2:3 | glued from behind: tilt and shadow only |
| `book` | `mq-item--book mq-item--volume` | 2:3 | spine shade left, page block right |
| `manga` | `mq-item--manga` | 5:7.4 | flat, square corners, no spine shade; light grey page block on the left, flush with the cover; volume number goes in the subline |
| `music` | `mq-item--vinyl` | sleeve 1:1, disc to the right | disc is part of the object, not content |
| `game` | `mq-item--game` | 5:7 | black console band above the art |

## Finish (default and variant)

Every item shows its medium's clean default. About one in three also gets the variant, chosen from the id: `Meuquarto.finish(id, category)` returns `null` (default) or `{ kind, corner?, size? }`. It starts from the same FNV-1a hash as the tilt and runs it through a 32-bit mixer (murmur3 `fmix32`), because FNV-1a leaves the high bits of neighbouring ids equal and runs of consecutive ids would otherwise all get (or all miss) the variant. On the mixed value, bits 0 to 11 pick the item (`% 3 === 0`), bits 12 to 15 the poster corner and bits 16 to 19 the poster size. The tilt keeps reading the plain hash, so tilt and finish are independent. Same id, same finish, on server and client.

| `category` | Variant (`kind`) | Classes on `mq-item` | How it is built |
| --- | --- | --- | --- |
| `movie`, `series`, `anime` | `peel`: peeled corner | `mq-item--peel mq-item--peel-br` (or `-bl`, `-tr`), `--peel-size: 14%..20%` | `clip-path` on `mq-art`, two triangles (shadow, back of the paper) as `::before` and `::after` of the cover; the shadow is a `drop-shadow` filter on the cover because `box-shadow` is clipped |
| `music` | `worn`: worn sleeve corners | `mq-item--worn` | rounded corners and a faded overlay |
| `book` | `ribbon`: bookmark | `mq-item--book` + a `<span class="mq-ribbon" aria-hidden>` before the art | darker `brand`, hangs below the bottom edge |
| `manga` | `gloss`: one soft diagonal highlight | `mq-item--gloss` | overlay on the cover |
| `game` | none | | |

- The peel never uses the top-left corner (the sticker lives there). The fold is 14% to 20% of the width, square.
- On hover the variant eases back (peel at 70%, worn and gloss at half strength), only without `prefers-reduced-motion`.
- Not decorated while loading or disabled. The variant is decoration: `aria-hidden`, no meaning.

Reference implementation for server code (uses `hash` above; `shape` is the medium's shape):

```js
const VARIANT={poster:'peel',vinyl:'worn',book:'ribbon',manga:'gloss'},CORNERS=['br','bl','tr'];
function mix(x){x^=x>>>16;x=Math.imul(x,0x85ebca6b);x^=x>>>13;x=Math.imul(x,0xc2b2ae35);x^=x>>>16;return x>>>0}
function finish(id,shape){const kind=VARIANT[shape],x=mix(hash(id));if(!kind||(x&4095)%3!==0)return null;
if(kind!=='peel')return{kind};return{kind,corner:CORNERS[((x>>>12)&15)%3],size:Math.round((14+6*((x>>>16)&15)/15)*10)/10}}
```

## States

| State | How | Look |
| --- | --- | --- |
| Normal | default | tilted, `shadow-poster` |
| Hover | pointer over the item (`is-hover` to document) | straightens and lifts 4px, `shadow-lift`; no motion with `prefers-reduced-motion` |
| Focus | `:focus-visible` on the link (`state="focus"` to document) | 2px `focus` ring, 6px offset |
| Disabled | `disabled` | 50% opacity, grayscale, not a link, no hover, no decorations or finish variant |
| Loading | `loading` | shimmering cover slot at the real ratio, two title bars, no tilt, no decorations or finish variant, `aria-busy` |
| Error | `error` | cover slot with `image-off` icon in `danger` and "Capa indisponível" |
| Empty (no cover) | no `cover` | hatched `surface-300` slot with the category icon, title below as usual |
| Empty shelf slot | `Meuquarto.HighlightSlot` | dashed `border-strong` slot with "Fixar obra" at the same ratio |

Do not put a placeholder wider or taller than the real cover, and do not print the title on a stand-in cover.
