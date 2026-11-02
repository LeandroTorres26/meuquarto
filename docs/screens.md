# Screens and navigation flows (input for design)

UI language pt-BR. Mobile-first (profile is mostly opened from social links).

## Screens
| # | Screen | Route | Auth | Purpose |
|---|---|---|---|---|
| S1 | Landing | `/` | no | Pitch, example profile, CTA to sign up |
| S2 | Sign up | `/cadastro` | no | e-mail, password, desired slug |
| S3 | Sign in | `/entrar` | no | e-mail + password |
| S4 | Public profile | `/{slug}` | no | Header (avatar, name, bio), "Agora", "Favoritos de tudo", highlights per category, "Ver quarto" button, TMDB/provider attribution footer |
| S5 | Room view | `/{slug}/quarto` (or modal) | no | Isometric scene built from highlights; back to profile |
| S6 | Editor: profile | `/painel` | yes | Name, bio, avatar upload, slug, theme picker |
| S7 | Editor: collection and highlights | `/painel/destaques` | yes | Tabs per category + "Favoritos de tudo". Per category: the highlights strip (reorder, limit indicator and upsell) and the whole collection below (search, sort, source badge, note, "Destacar", "Ocultar"/"Remover", "Adicionar obra" opens S8) |
| S8 | Search/pin dialog | modal in S7 | yes | Search catalog, result cards, pin |
| S9 | Linked accounts | `/painel/contas` | yes | Provider cards: link username, status, last sync, "sync now" (cooldown), unlink |
| S10 | "Agora" settings | `/painel/agora` | yes | Per category: auto item, pin manually, hide |
| S11 | Store | `/painel/loja` | yes | Theme/pack catalog, preview, buy |
| S12 | Checkout result | `/painel/loja/sucesso` `/cancelado` | yes | Return from Stripe; waits for webhook confirmation |
| S13 | My purchases | `/painel/compras` | yes | Orders and owned items |
| S14 | Account | `/painel/conta` | yes | Change password, delete account (confirmation) |
| S15 | Terms / Privacy | `/termos` `/privacidade` | no | Static legal text (`LegalLayout`), dated via `src/lib/legal.ts`; revise the texts whenever stored data, cookies or providers change. Linked from sign-up, landing, profile footer and the cookie notice (to `/privacidade`) |
| S16 | 404 / profile not found | | no | |
| S19 | Forgot password | `/esqueci-senha` | no | E-mail field; same confirmation message whether or not the account exists ("Se existir uma conta com esse e-mail, enviamos um link."). Linked from S3. |
| S20 | Reset password | `/redefinir-senha?token=…` | no | New password + confirmation; states: valid, expired/used link (offer to request again), success (go to S3). |
| S18 | Category listing | `/{slug}/{categoria}` (`filmes`, `series`, `animes`, `mangas`, `livros`, `musica`, `games`) | no | "Ver todos" target. Whole visible collection of one category: cover grid, search by title, sort (recentes, título, ano), infinite scroll or pages, highlight badge, empty and no-result states. Click opens S17. |
| S17 | Item details | modal in S4/S5, `?item={mediaItemId}` | no | Opened by clicking any highlight or "Agora" card. Cover, title, year, category sticker, personal note, origin ("assistido em 3 out. no Letterboxd" when synced), external links ("Ver no Letterboxd", "Ver no TMDB"...). Centered dialog from 600px, bottom sheet on mobile. URL param makes it shareable; back button and Esc close it. In S5 it is the existing info panel. |

Implementation notes for S4 and S17 (issues #16 and #17):
- `/{slug}` is the dynamic segment `frontend/src/app/[slug]`. Static routes (`/painel`, `/entrar`, `/cadastro`) win over it and `/api/*` is rewritten by `proxy.ts` before routing, so no reserved word is shadowed. An unknown, reserved or malformed slug renders S16 (the API answers all of them with the same 404). The page streams inside Suspense, so that response is a 200 with `noindex`, not an HTTP 404 (Next.js cannot change the status after streaming starts).
- S17 is the `?item={ref}` state of S4, where `ref` is the owner's collection item id (not `media_items.id`). Opening and closing it changes the URL with `history.pushState` and never refetches the profile. The dialog fetches `GET /api/public/profiles/{slug}/items/{ref}` through the same-origin proxy.
Implementation notes for S5 (issue #43, ADR 0013: CSS 3D ported to React):
- `/{slug}/quarto` is `frontend/src/app/[slug]/quarto/page.tsx`. `quarto` is a static segment, so it wins over the dynamic `[categoria]` of S18, and it is not a category route (`CATEGORY_META.*.route`); the backend already keeps `quarto` in its reserved slug list (`SlugReservedRule`), so no profile can shadow it. Unknown profile: S16.
- Data: the same `GET /api/public/profiles/{slug}` as S4 (no new endpoint, no provider call). The server component sends only what the scene draws and lists (ref, category, title, year, cover) to the client chunk. The room uses the highlights only; "Agora" and "Favoritos de tudo" are not in the prototype's room.
- "Ver quarto" on S4 (`ProfileHeader`) is a `Link` with `prefetch={false}`. The scene (`RoomLoader`) is a `next/dynamic` import with `ssr: false`, so the code of the room is its own chunk, loaded only on this route; S4's bundle and load time do not change.
- Groups: films and series on the wall (`parede`, up to 4 covers), anime on the mural (3), books and manga on the shelf (up to 7 books and 8 volumes, spine colors picked by the work), music on the record player (the first album on the table, up to 3 more in the crate) and games on the TV (the first cover is the screen). The scene draws what fits; the panel lists every highlight of the group. A group with no highlights keeps its furniture (empty frames, empty shelf) and the panel says so. Covers are plain `<img>` (hotlinked, https only); the 3D faces do not carry the media finish classes (the prototype's faces do not either): the finish shows in S17 inside the panel, which reuses `ItemDetail`.
- Code: `frontend/src/features/room/` (`camera.ts`, `box.ts`, `layout.ts` pure and unit tested; `objects/` the `RoomObject` hierarchy; `CameraController.ts` holds the camera outside React state; `RoomScene`, `ObjectView`, `LabelLayer`, `RoomPanel` render it). The item open in the panel is `?item={ref}` like S17 (a pushed history entry: Back and Esc close it, a shared link opens it).
- Input and layout follow the S5 section of `docs/design/telas/README.md` (prototype URL params `?modo`, `?objeto`, `?hover`, `?dica` and `?painel` are not part of the app). Share button: the Web Share API when the browser has it, otherwise it copies the room link.
- DOM budget of a full room (every group filled): 74 boxes, 319 faces and 12 cover images in the 3D tree (405 nodes), about 560 nodes in the whole page.

- Not built yet and therefore left out of S4: the avatar, tagline and bio (not in the profile response) and the "Ver todos" link when a category has more items than the shelf shows (it leads to S18; `CATEGORY_PAGE_READY` in `frontend/src/lib/profile-view.ts` can switch it off). The Deezer preview in S17 is optional and not built.

Implementation notes for S10 and the "Agora" block of S4 (issue #35):
- S10 is `frontend/src/app/painel/agora`, from `painel-agora.html`: per category the item shown, a caption (source, "Escolhido por você", "Escondido no perfil", or the link to connect the platform) and the three-way control Automático / Escolher / Esconder. Automático is disabled for Séries (no platform). Escolher opens the owner's own collection of the category to pick from. Each change is one `PUT /api/now/{category}` whose answer replaces that category's entry.
- The block on S4 is `NowBlock` (the design's `.now` aside with `.mq-now` rows, `NowCard` markup) inside `profile-top__in`; titles are `?item=` links that open S17. Verbs: Assistindo (filme, série, anime), Lendo (livro, mangá), Ouvindo (música), Jogando (game). Status: "Letterboxd, há 2 dias" (platform and time of the activity) or "Escolhido por você". Categories with nothing to show are left out, and the block disappears when none is left. The episode progress and "tocando agora" of the prototype are not available (sync keeps no progress).

Implementation notes for S18 (issue #26):
- `/{slug}/{categoria}` is `frontend/src/app/[slug]/[categoria]`. An unknown category segment renders S16 (route variant); an unknown slug renders S16 (profile variant). Server-rendered from `GET /api/public/profiles/{slug}/collection` (24 per page, the API caps at 48).
- Search, order and page live in the URL: `?q=`, `?ordem=recentes|titulo|ano`, `?pagina=N` (defaults omitted). Pages are previous/next links instead of infinite scroll, so every page has its own address and works without JavaScript. Searches are `noindex`.
- Cards are the S4 `HighlightCard` with the "Destaque" seal; clicking one opens S17 over the listing (`?item=` is added to the current query).

Cross-cutting components: cookie banner, toasts, empty states (no highlights yet), limit-reached upsell, loading skeletons, error states for failed sync.

## Main flows
1. **Onboarding:** S1 → S2 → S6 (set avatar/bio) → S9 (link accounts, first sync) → S7 (pin extras) → copy link/share.
2. **Pin a highlight:** S7 → S8 search → select → appears in list → add note → reorder. At limit → upsell → S11.
3. **Link and sync:** S9 → choose provider → enter username → validation → first sync → status → "Agora" fills (S10 to adjust).
4. **Buy a theme:** S11 → preview → Stripe Checkout (Pix/card) → S12 → webhook grants → theme selectable in S6.
5. **Visitor:** shared link (Open Graph preview) → S4 → click a highlight → S17 → external link or close → "Ver quarto" → S5 → back → "Create yours" CTA → S2.
6. **Delete account:** S14 → confirmation with password → S1.

## Design inputs
- Brand: name "meuquarto"; metaphor of a personal room; Brazilian audience.
- Profile themes are original (abstract/animated backgrounds, frames), never franchise assets.
- Room view style open (isometric 2D vs minimal Three.js) — design decides, then ADR 0013.
