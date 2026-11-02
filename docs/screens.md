# Screens and navigation flows (input for design)

UI language pt-BR. Mobile-first (profile is mostly opened from social links).

## Screens
| # | Screen | Route | Auth | Purpose |
|---|---|---|---|---|
| S1 | Landing | `/` | no | Pitch, example profile, CTA to sign up |
| S2 | Sign up | `/cadastro` | no | e-mail, password, desired slug |
| S3 | Sign in | `/entrar` | no | e-mail + password |
| S4 | Public profile | `/{slug}` | no | Header (avatar, name, bio, "Copiar link" / "Compartilhar" and "Ver quarto", links with platform marks to the owner's profiles on other platforms, ADR 0023), "Agora", "Favoritos de tudo", highlights per category, "Ver quarto" button, TMDB/provider attribution footer |
| S5 | Room view | `/{slug}/quarto` (or modal) | no | Isometric scene built from highlights; back to profile |
| S6 | Editor: profile | `/painel` | yes | Name, bio, avatar upload, slug, theme picker |
| S7 | Editor: collection and highlights | `/painel/destaques` | yes | Tabs per category + "Favoritos de tudo". Per category: the highlights strip (reorder, limit indicator and upsell) and the whole collection below (search, sort, source badge, note, "Destacar", "Ocultar"/"Remover", "Adicionar obra" opens S8) |
| S8 | Search/pin dialog | modal in S7 | yes | Search catalog, result cards, pin |
| S9 | Linked accounts | `/painel/contas` | yes | One card per platform, same rows for all (ADR 0023): head (name, category stickers), body (username field, or the profile link and the sync status), note ("Atualiza sozinho." with what enters the collection, or "Só o link." for platforms without a readable list), action ("Vincular X"; "Sincronizar agora" with cooldown and "Desvincular"; link-only cards only "Desvincular"). Sections "Vinculadas" and "Vincular mais". Platforms: Letterboxd, AniList, MyAnimeList, Last.fm, Steam, Goodreads (sync); Backloggd, Trakt, StoryGraph (link only). The card name carries the platform mark |
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

## Session states

Public screens are built for three viewers, and every new screen must say what changes for each one:
1. **Visitor**: no session.
2. **Signed in, on someone else's page** (or on a page with no owner: landing, legal, 404).
3. **Signed in, on their own profile** (S4, S18, S5 where the slug is theirs).

| Screen | Visitor | Signed in (other) | Signed in (own) |
| --- | --- | --- | --- |
| Top bar of public pages (S1, S4, S18, S15, S16) | Entrar, Crie o seu | Meu perfil, Painel | Destaques, Editar perfil |
| Profile footer "Crie o seu" (S4, S18, S16) | shown | hidden | hidden |
| Landing S1 hero | pitch + "Crie o seu" | "Ir para o painel", "Meu perfil" | n/a |
| S16 (404) sign-up button | "Usar esse endereço" / "Crie o seu" | "Ir para o painel" | n/a |
| S2 Cadastro, S3 Entrar, S19 Esqueci a senha | form | redirect to `/painel` | n/a |
| Room S5 panel "Crie o seu quarto" | shown | hidden | hidden (the room has its own share button) |
| Empty states on the owner's own profile (S4, S18) | neutral text | neutral text | text + link to `/painel/destaques` |
| Platform links in the S4 header (ADR 0023) | shown | shown | shown; with no linked account, "Vincule seus perfis" to `/painel/contas` instead (visitors see nothing) |
| Theme (ADR 0022) | `noite` (or the last theme kept on that browser) | their own theme, except S4, S18 and S5, which show the owner's | their own theme |

Notes:
- S5 is full screen and has no top bar; only its panel call to action follows the table. S20 (`/redefinir-senha`) is not redirected: the link comes from an e-mail and works whoever is signed in.
- "Painel" is `/painel`, "Destaques" is `/painel/destaques`; "Meu perfil" is `/{slug}` of the session; "Editar perfil" is `/painel` (S6).
- Implementation (issue #118): the pages stay prerendered (Partial Prerender, `cacheComponents`). Everything that depends on the session is a small server component that reads the cookie inside its own `<Suspense>` (`components/SessionActions.tsx`, `components/SessionSlots.tsx`), so the cached HTML holds only an invisible, inert placeholder of the same size (no layout shift, never a wrong call to action) and nothing user-specific. The decision is the pure `viewerState(pageSlug, session)` in `lib/viewer-state.ts`. The room panel receives its call to action as a server element prop (`invite`). Redirects of S2, S3 and S19 are real HTTP redirects in `proxy.ts`.

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

Implementation notes for S6 (issue #15): `frontend/src/app/painel/page.tsx` and `ProfileEditorScreen`, from `painel-perfil.html`. Fields: photo (polaroid, "Enviar foto"/"Trocar foto", "Remover foto"), name (max 40), bio (max 160, live counter), address (slug; changing it frees the old one, no redirect), theme picker with the four themes (#37: the API sends `themes` with `premium`, `owned` and `locked` flags; a locked premium theme can be previewed but is never sent on save, and points to the store `/painel/loja`, built in #38). The photo uploads as soon as it is chosen, with a client-side preview as a data URL (no `blob:` in the CSP) and a precheck of size and type; the server decides by content. Not built because the profile has no data for them: the photo caption and the handwritten tagline. Errors come as API codes (`AVATAR_UNSUPPORTED_TYPE`, `AVATAR_DIMENSIONS_TOO_LARGE`, `SLUG_TAKEN`...) mapped to pt-BR in `lib/profile-editor.ts`. On S4 the header shows the polaroid (the owner's initial when there is no photo), the name, the bio (line breaks kept) and applies the theme with `data-theme` on the profile content; the top bar and footer follow the theme too (#37: `ProfileShell` puts `data-theme` on the whole page, and a premium theme uses the mono logo). S6 also draws a small profile in the picked theme (`ThemePreview`). Picking a theme recolours the whole site at once and saves it on its own (ADR 0022: a `PUT /api/profile` with the other saved fields, then the `mq_theme` cookie that the root layout reads before the first paint); a locked premium theme recolours the site only while S6 is open and is never saved. A failed save shows the specific pt-BR message for known codes and "Tentar de novo" for a 5xx or a lost connection.

Implementation notes for S10 and the "Agora" block of S4 (issue #35):
- S10 is `frontend/src/app/painel/agora`, from `painel-agora.html`: per category the item shown, a caption (source, "Escolhido por você", "Escondido no perfil", or the link to connect the platform) and the three-way control Automático / Escolher / Esconder. Automático is disabled for Séries (no platform). Escolher opens the owner's own collection of the category to pick from. Each change is one `PUT /api/now/{category}` whose answer replaces that category's entry.
- The block on S4 is `NowBlock` (the design's `.now` aside with `.mq-now` rows, `NowCard` markup) inside `profile-top__in`; titles are `?item=` links that open S17. Verbs: Assistindo (filme, série, anime), Lendo (livro, mangá), Ouvindo (música), Jogando (game). Status: "Letterboxd, há 2 dias" (platform and time of the activity) or "Escolhido por você". Categories with nothing to show are left out, and the block disappears when none is left. The episode progress and "tocando agora" of the prototype are not available (sync keeps no progress).

Implementation notes for S18 (issue #26):
- `/{slug}/{categoria}` is `frontend/src/app/[slug]/[categoria]`. An unknown category segment renders S16 (route variant); an unknown slug renders S16 (profile variant). Server-rendered from `GET /api/public/profiles/{slug}/collection` (24 per page, the API caps at 48).
- Search, order and page live in the URL: `?q=`, `?ordem=recentes|titulo|ano`, `?pagina=N` (defaults omitted). Pages are previous/next links instead of infinite scroll, so every page has its own address and works without JavaScript. Searches are `noindex`.
- Cards are the S4 `HighlightCard` with the "Destaque" seal; clicking one opens S17 over the listing (`?item=` is added to the current query).

Implementation notes for S19 and S20 (issue #53, no prototype: built from the S3 layout and classes):
- S19 `/esqueci-senha`: one e-mail field, `POST /api/auth/password-reset`. After sending, the page shows "Se existir uma conta com esse e-mail, enviamos um link." plus "O link vale por 30 minutos e só funciona uma vez" whatever the answer was (the API answers 202 for known, unknown and malformed e-mails). Linked from S3 ("Esqueci minha senha", under the password field). Rate-limited answers show the usual "Tente de novo em X minutos".
- S20 `/redefinir-senha?token=…`: the client reads the token from the address once and rewrites the address without it (`history.replaceState`); the page is `noindex` and sent with `Referrer-Policy: no-referrer` (`next.config.ts`) and `<meta name="referrer">`. States: valid (new password + repetition, "Pelo menos 8 caracteres"; password-rule errors under the field, the link stays usable), expired/used/tampered/missing token ("Link inválido ou expirado", button "Pedir um novo link" to S19; the API gives one generic `INVALID_RESET_TOKEN` for all of them), success ("Senha alterada", button "Entrar" to S3; every session was ended). A reload after the first load also lands on the invalid state, by design, because the token is no longer in the address.

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
- Room view style: CSS 3D in React ([ADR 0013](adr/0013-room-rendering-technology.md)).
