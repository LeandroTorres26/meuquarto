# meuquarto screens (design reference)

Static HTML prototypes of every screen, built only with the design system in `../../design-system/` and the layout system in `site.css`. They are the source for implementation; the Claude Design canvas (https://claude.ai/artifact/Bph2ex7pUrk6mLWsCD9wGi) shows the same screens side by side at desktop, tablet and phone width.

Open them through any static server rooted at `docs/` (paths go up to `../../design-system/`), for example `python -m http.server 8765 --directory docs` and then `http://localhost:8765/design/telas/perfil.html`. Resize the window to see the three layouts.

Read in this order:
1. `../../design-system/README.md` (voices, proximity levels, grid, visual rules) and `tokens.json`.
2. `site.css` (layout classes; every value is a token).
3. The screen you are implementing.

## Files

| ID | File | Route | Notes |
| --- | --- | --- | --- |
| S1 | landing.html | / | Hero, steps, integrations, room teaser, plans, cookie notice ("Entendi" closes it for good; remembered per browser, `?cookies=1` shows it again; canvas prop `cookies`). |
| S2 | cadastro.html | /cadastro | Slug check (available state). |
| S3 | entrar.html | /entrar | Error state. |
| S4 | perfil.html | /{slug} | Public profile: only the highlights. Each section shows the collection count ("24 animes") and "Ver todos" to S18 only when the collection has more items than the highlights shown (Mangás has 4 of 4, so it has none; Favoritos de tudo never has one). Every HighlightCard and Agora row links to `?item=<id>` (S17). |
| S5 | quarto.html | /{slug}/quarto | Protótipo em CSS 3D. Tecnologia final a decidir; manter o mesmo conteúdo, a mesma câmera e os mesmos controles. Full screen, two input models (mouse and touch): see "S5 room" below. The info panel shows the S17 content when an item is tapped (same `?item=<id>`). |
| S6 | painel-perfil.html | /painel | Profile form + theme picker. |
| S7 | painel-destaques.html | /painel/destaques | Per category (tabs, Favoritos de tudo first): the highlights strip on top (wall order, arrows to reorder, remove, meter "4 de 4", limit upsell) and the whole collection below (search, sort Recentes/Título/Ano; each row: origin badge "Letterboxd", "AniList"… or "Manual", note, "Destacar" / "Remover dos destaques", menu with "Ocultar da coleção" for synced items or "Remover da coleção" for manual ones, both with "Desfazer"). "Adicionar obra" opens S8. At the limit "Destacar" is disabled and points to the upsell; adding to the collection stays free. Fully interactive prototype. |
| S8 | busca.html | modal in S7 | "Adicionar anime": each result has "Adicionar à coleção", or "Na coleção" when it is already there. No limit on the collection. Bottom sheet on phone, centered dialog from 600px. |
| S9 | painel-contas.html | /painel/contas | Connected (synced, cooldown, error) and not connected (empty, invalid user, CSV import). Each provider says in a caption what enters the collection. |
| S10 | painel-agora.html | /painel/agora | Automático / Escolher / Esconder per category. |
| S11 | loja.html | /painel/loja | Themes and pack; same-height cards. |
| S12 | loja-sucesso.html | /painel/loja/sucesso, /cancelado | Confirmed state; the comment at the top describes waiting and cancelled. |
| S13 | compras.html | /painel/compras | Owned items + orders table. |
| S14 | conta.html | /painel/conta | Password, sign out, delete account. |
| S16 | 404.html | — | Profile not found. |
| S17 | perfil.html?item={id} | /{slug}?item={id} | Item details. Dialog over S4, bottom sheet below 600px, centered dialog (max 560px) from 600px. Built by `item.js` (data and markup, shared with S5) and `item-dialog.js` (native `<dialog>`: Esc, close button with `aria-label`, click on the dimmed backdrop and the browser back button all close it; focus returns to the card). Order: cover in the medium's shape with sticker and the finish variant (from the id), title (2 lines), year and subtitle, owner note on paper (only if there is one), origin caption, music preview (Deezer, 30s, optional), external links: activity platform first, then catalog, new tab. |
| S18 | categoria.html?c={categoria} | /{slug}/{categoria} | Public category listing: owner link back to the profile, category name and collection count, search by title, sort (Recentes, Título, Ano), cover grid in the media shapes (2 / 4 / 6 columns), discreet "Destaque" seal on highlighted items, infinite scroll of 12 with skeletons in the medium's shape; a cover opens S17 (`&item=<id>`). Categories: filmes, series, animes, mangas, livros, musica, games. |

Implementation notes for S9 (issue #28): the page shows one card per platform. LETTERBOXD, ANILIST and GOODREADS can be linked (Goodreads asks for the numeric profile id, with help text on where to find it; issue #34); STEAM and LASTFM have no backend provider yet, so their cards are disabled with "Em breve" (no input) until each provider exists (flip `available` in `frontend/src/lib/linked-accounts.ts`). Buttons use the screen's copy: "Sincronizar agora" (with "Aguarde N min" from `nextManualSyncAt`, BR-03) and "Desvincular" (confirmation dialog explaining FR-16), instead of the prototype's "Atualizar" and "Desconectar". After a sync the card links to the public profile.

Implementation notes for S7 and S8 (issue #88): the page is `frontend/src/app/painel/destaques/page.tsx` (first paint on the server, changes from the browser with CSRF). Deviations from the prototype, all because of the backend contract: (1) "Favoritos de tudo" has no collection of its own, so its collection section has a category select ("Categoria da coleção") and each row keeps the same "Destacar" / "Remover dos destaques" buttons as in a category; the highlight in the favorites list is created with `category: null`. (2) The collection search box filters by title over the pages already loaded (the API has no text search yet) and says so while more pages exist ("Carregar mais"). (3) "Mostrar ocultas" is a checkbox next to the sort; hidden rows get an "Oculto" badge and "Mostrar na coleção" in the menu. (4) Upsell: the pack is not for sale yet, so the box says it "chega em breve" and has no price or button (the prototype's "Ver o pack" returns with S11). (5) "Desfazer" after removing a manual work adds the work again, without its note and highlight. (6) Reordering works by drag and drop (mouse) and by the "Mover para antes / depois" buttons (keyboard, touch, screen readers); both save with one PUT. (7) S8 stays open after an addition so several works can be added in a row; each result turns into "Na coleção". Categories without a catalog (or a catalog without key) answer `available: false` and the dialog says "A busca de ... ainda não está disponível".

## Collection and highlights

Each category has a **collection** with no limit, fed by the linked accounts (S9 says what each one brings) and by manual additions (S8). The **highlights** are items the owner marks inside the collection: up to 4 per category on the free plan, 12 with the pack (and 4 / 12 in Favoritos de tudo). Only highlights appear on the profile home (S4) and in the room (S5); the whole collection is on S18. S17 shows the note and the origin of the collection item. In the prototypes all of it comes from `item.js` (`MQItem.cats`, `collection(slug)`, `isHighlight`, `card`), so S4, S7, S17 and S18 show the same data.

## S5 room

The input model comes from `@media (hover: hover)` (mouse) or `(hover: none)` (touch); `quarto.html` sets `.room--touch` on the page. Prototype-only URL params: `?modo=toque|mouse` forces a model, `?objeto=<grupo>` starts with a selection, `?hover=<grupo>` fakes a hover, `?dica=1` replays the first-visit hint (`?dica=fixa` keeps it on screen, `?dica=0` never shows it). Groups: `parede` (Pôsteres), `mural`, `estante`, `vitrola`, `tv`. In the canvas the same states are the props `modo`, `cena` (nada, hover, selecionado, dica) and `objeto`, with one board per state.

Layout: the room never sits under the panel. `quarto.html` measures the free area (below the top bar; above the sheet and the tools, or left of the side panel) and fits the room's projected bounds into it, computed once over every yaw and pitch so the size does not change while rotating; zoom keeps the room's center on the center of that area. The panel is a side panel (`.room--side`) with a mouse from 1024px, and in any landscape window from 600px (width > 1.2 × height), touch included, with the chips pinned to its bottom; otherwise it is the bottom sheet. When the panel changes size (a selection, an item), the room eases into the new area.

Panel content, top to bottom: the title ("O quarto de Leandro"), the selected object's items (or S17 when an item is open), the object chips (navigation), then "Crie o seu quarto". There is no how-to text in the panel: it is the scene's accessible description, and on touch it also shows in the first-visit hint.

Collapsible panel: a button at the panel's top right ("Recolher painel", or "Recolher atalhos" on touch; aria-expanded, aria-controls) folds it into one small button: "Mostrar painel" at the top right in the side layout, or bottom right in the bottom layouts ("Atalhos" on touch). The room refits to the freed space and focus moves to the remaining button. Closing the panel also clears the selection: its label and glow go away (hover and focus still show a label while the pointer or focus is on an object). Selecting an object opens the panel again. The choice is remembered per viewer (`localStorage` key `mq-quarto-painel`); `?painel=recolhido|aberto` forces it in the prototype, and the canvas has the prop `painel`.

The scene is not a document: nothing in it can be selected, dragged out or long-pressed (no text selection, no native image drag, no context menu, no iOS callout). Required in the final implementation too, whatever the 3D technology. Hidden labels and their wrappers let the pointer through, so only a visible label (or a touch dot) sits above the objects.

Camera limits, shared by both models:

| | Range | Notes |
| --- | --- | --- |
| Rotation (yaw) | -88° to -2° (starts at -45°) | Horizontal drag, 0.35° per px. The buttons turn 15°. |
| Tilt (pitch) | 22° to 34° (starts at 28°) | Mouse only, vertical drag. `pitch = 28 + 6 · tanh((raw − 28) / 6)`, so it slows into the limits instead of stopping dead. Never shows under the floor or over the cut walls. |
| Pan | Room stays inside the free area when smaller than it, and always covers it when larger (24px of slack) | Mouse: drag with the right or middle button, or hold Space or Shift and drag (cursor: move); Shift + wheel pans sideways. Touch: move two fingers together (same gesture as the pinch). "Centralizar o quarto" (scan icon) resets pan, zoom, rotation and tilt. |
| Zoom | 0.6× to 3.5× of the fitted size (starts at 1×) | Wheel (`× exp(−deltaY · 0.0015)`), trackpad pinch (wheel with ctrlKey, `· 0.01`), touch pinch, buttons ×1.25 / ÷1.25. Centered on the room and eased toward the target (0.18 of the gap per frame). A touch selection frames the object at 1.6×. |

Mouse (`hover: hover`):
- No label by default. Hovering an object shows only its label above it (fade and a 6px rise, 180ms ease-out) and a soft `brand` glow on the object's edges (`.bx.is-lit`: 2px outline plus drop-shadow), with the same transition. Leaving reverses both. The rest of the room does not change.
- A click selects: the label (brand fill) and the glow stay, and the info panel shows the group. Hovering another object shows its label and glow too, without losing the selection.
- Each object's label is a button, so Tab reaches every object; focus shows label and glow like hover.
- The wheel zooms only over the scene and the page never scrolls (non-passive listener). The zoom buttons stay, smaller (36px), in one tools group with rotation, "Centralizar o quarto" and the lamp, as the accessible alternative.
- Pan like in Figma: right or middle button drag, or Space / Shift + drag. The right-click menu is off over the scene.

Touch (`hover: none`):
- Full screen with no page scroll behind it, "Voltar ao perfil" at the top and the camera tools in a translucent row right under it (the empty band above the room), so they never cover the room.
- Each object has a small paper dot (8px) that breathes softly (opacity, 2.6s), always visible, inside a 44px target; no text label by default. It hides while the object's label shows.
- Nothing selected: the sheet is only a slim translucent dock with the chips (Pôsteres, Mural, Estante, Vitrola, TV; 44px tall), the main path on phones. The title stays for screen readers only; the how-to lives in the first-visit hint; "Crie o seu quarto" shows in the open sheet. On tablets the dock is as wide as its chips.
- Tapping a chip or an object selects it: the object glows, its label shows, the sheet opens above the chips (handle, group, items, "Crie o seu quarto"; at most 52% of the height), and the camera pans and zooms to frame the object between the tools and the sheet. Tapping empty space, dragging the sheet down by its handle or title (more than 72px), or Esc clears the selection and brings the camera back.
- One finger dragging horizontally rotates; two fingers pinch to zoom and move together to pan. No tilt on touch: vertical drag belongs to the sheet.
- First visit: every label shows for 2s with the hint "Toque em um objeto ou use os atalhos abaixo. Arraste para girar, use dois dedos para mover e aproximar." above the dock, then they fade (400ms) and do not come back (`localStorage` key `mq-quarto-dica`).

Reduced motion: labels and glow only fade (100ms, no rise), the dots do not breathe, and the camera jumps to its target instead of easing.

S17 music preview: a round play button sits on the album sleeve (paper disc; brand while playing). Playing slides the record out and spins it, a ring around the button fills over 30s, then it stops. The credit "Prévia de 30 segundos, pela Deezer." sits under the cover.

## States

Screens with more than one state show the default; open them with `?estado=<name>` to see the others (`estados.js`). In the canvas the same states are a `data-props` tweak with the same names.

| Screen | Default | Other states | Canvas prop |
| --- | --- | --- | --- |
| S7 painel-destaques.html | `?aba=filmes` (strip full, upsell, "Destacar" disabled) | `?aba=<categoria>` or `?aba=favoritos`; `&pack=1` raises the limit to 12. Remove a highlight to see "Destacar" enabled; open a row's menu for Ocultar / Remover. | `aba`, `pack` |
| S8 busca.html | `resultados` | `vazio`: no results message. `erro`: network error with "Tentar de novo". In the canvas, "Adicionar à coleção" turns into "Na coleção" with a toast. | `estado` |
| S9 painel-contas.html | `normal` | `sincronizando`: AniList shows a spinner and a disabled "Sincronizando…" button. | `estado` |
| S12 loja-sucesso.html | `confirmado` | `aguardando`, `cancelado` (canvas only; described in the page comment). | `estado` |
| S17 perfil.html?item= | an item | `?item=perfect-days` note + synced (Letterboxd then TMDB). `?item=twin-peaks` no note + chosen by hand (TMDB only). `?item=clube-da-esquina` music with the play button on the sleeve (click it for the playing state). `?item=cowboy-bebop&estado=carregando` loading: the cover slot keeps the medium's proportion. `?item=qualquer-outro` an id that no longer exists: "Esse item saiu do quarto.". | `midia` (filme, serie, anime, manga, livro, game, musica), `nota` (com, sem), `origem` (sincronizado, manual; séries are always by hand), `estado` (pronto, carregando, removido), `previa` (parada, tocando, sem; music only). S5 canvas: `item` opens an item in the room panel. |
| S18 categoria.html | `?c=animes`, full | `?q=zzz` search with no result ("Limpar busca"); `?estado=vazia` empty collection; scroll to the end to see the skeletons; `?ordem=titulo|ano`. | `categoria`, `estado` (cheia, carregando, busca-vazia, vazia) |

Rules shown as fixed content: S9 CSV import is disabled ("Em breve", roadmap); S10 Séries has no sync platform, so "Automático" is disabled and the row says "Séries não têm plataforma para sincronizar. Escolha à mão."; provisional prices are R$ 14,90 (pack 4 → 12) and R$ 9,90 (each premium theme). Free themes: Noite and Dia; premium: Locadora and Estufa (see the design system README).

## Paths

| In the canvas (`.dc.html`) | In the repo |
| --- | --- |
| `ds/meuquarto/` | `docs/design-system/` |
| `site.css` | `docs/design/telas/site.css` |
| `/_blob/<id>` | `covers/cNN.svg` (table below) |
| `./support.js` | does not exist; canvas runtime only, not needed |
| `<x-import component-from-global-scope="Meuquarto.X">` | `Meuquarto.X` React component from `bundle.js`, or its static markup |
| `<Name>Tablet.dc.html`, `<Name>Mobile.dc.html` | previews only; the page is the same file at another width |

| Blob id | File |
| --- | --- |
| 5305bfe4382792bcb23d0e342048a919 | covers/c01.svg |
| 9d03eed4a3f810d1e4fdef7e3789ae88 | covers/c02.svg |
| a3a20f7d89f1ef7f6919ab68cd566dbd | covers/c03.svg |
| 30304e2ec90a3672235da0eeb67f01c8 | covers/c04.svg |
| a8c5fd5d29d8bf39d9cef883e6113a8b | covers/c05.svg |
| 02e37164133096b15c2c84b1dc01a73f | covers/c06.svg |
| 0548b88cfdd9e4e714dcfa4f4d42597d | covers/c07.svg |
| ace28a3483353ead5822972c8084c6d0 | covers/c08.svg (avatar) |
| aa8309afd7b3a04e4d8e1bb62bd644b6 | covers/c09.svg |
| fa9635153e9465cb18f95e21b1d7285d | covers/c10.svg |
| b9dbe28d95e64b1008ebe105d0d014b5 | covers/c11.svg |
| 2f2d749028da3c743c966dfcae9a48b3 | covers/c12.svg |

`icons.svg` is a Lucide sprite (`<svg class="icon"><use href="icons.svg#name"/></svg>`). `covers/` holds original abstract placeholder covers; in the app they are real covers from TMDB, AniList, Open Library, IGDB and Deezer.

Media cards are written as the static markup of `Meuquarto.HighlightCard` with `--tilt-f` already computed from each id (see `HighlightCard/README.md` for the function); in React use the component.

## Layout system (`site.css`)

Breakpoints: phone < 600px, tablet 600 to 1023px, desktop >= 1024px.

| Variable | Role | Phone | Tablet | Desktop |
| --- | --- | --- | --- | --- |
| `--cols` | layout columns | 4 | 8 | 12 |
| `--gutter` | page side margin | 16 | 24 | 32 |
| `--l1` | inside an element (title + meta, label + field) | 8 | 8 | 8 |
| `--l2` | inside a group (list items, title to content) | 16 | 16 | 16 |
| `--l3` | between groups; page head to content | 24 | 32 | 32 |
| `--l4` | between sections | 48 | 64 | 64 |
| `--l5` | page end | 64 | 96 | 96 |
| `--inset` | padding of a region | 16 | 24 | 32 |

Form fields are 24 apart and form groups 48 apart (`.form-groups`), so a group title (16 above its first field) always reads as part of its group.

Key patterns:
- `.sections` / `.groups` / `.stack` / `.pair` / `.pair-2`: vertical rhythm by container gap, never margins on children.
- `.page-head`: title + description; the next block starts at L3.
- `.sec-head` (`--media` when covers follow: stickers overlap the cover top).
- `.shelf`: cover grid aligned to the page columns, covers standing on a common baseline. `.shelf--scroll` scrolls sideways on phones. `.shelf-row` puts a category label in columns 1 to 3 and its covers in columns 5 to 12 on desktop.
- `.profile-top`: identity left (8 columns), Agora right (4 columns) on desktop; Agora becomes a sideways strip below 1024px.
- `.pshell` / `.pnav` / `.pmain`: painel nav as tabs below 1024px, a grouped side menu (Perfil, Destaques, Agora, Contas | Loja, Compras, Conta) on desktop. Inside `.pmain`, page head to first block is L3; add `.next` to later blocks for L4.
- `.region`: a box only for repeated objects (an account, a store item, a pinned work) and for focus (a warning, the danger zone). Sections are grouped by heading and space, not boxes.
- `.cols-aside`: main task (3 fr) and supporting column (2 fr) side by side on desktop.

## Type in use

Identity (Big Shoulders, uppercase): `.t-display-xl` landing hero, `.mq-profile__name` / `.t-display` profile name, `.t-poster` cover and Agora titles, stickers. Interface (Figtree, sentence case): `.t-h1` page title, `.t-h2` section, `.t-h3` group, `.t-lead`, `.t-body`, `.t-sm`, `.t-label`, `.t-cap`. One `h1` per page; never skip a level for size.
