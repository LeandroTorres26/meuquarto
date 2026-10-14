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
| S15 | Terms / Privacy | `/termos` `/privacidade` | no | Legal text |
| S16 | 404 / profile not found | | no | |
| S18 | Category listing | `/{slug}/{categoria}` (`filmes`, `series`, `animes`, `mangas`, `livros`, `musica`, `games`) | no | "Ver todos" target. Whole visible collection of one category: cover grid, search by title, sort (recentes, título, ano), infinite scroll or pages, highlight badge, empty and no-result states. Click opens S17. |
| S17 | Item details | modal in S4/S5, `?item={mediaItemId}` | no | Opened by clicking any highlight or "Agora" card. Cover, title, year, category sticker, personal note, origin ("assistido em 3 out. no Letterboxd" when synced), external links ("Ver no Letterboxd", "Ver no TMDB"...). Centered dialog from 600px, bottom sheet on mobile. URL param makes it shareable; back button and Esc close it. In S5 it is the existing info panel. |

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
- Room view style open (isometric 2D vs minimal Three.js) — design decides, then ADR 11.
