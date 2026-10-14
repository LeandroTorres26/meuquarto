# Prompt para o Claude Design: modal de detalhes do item (S17)

Cole no Claude Design, no canvas "meuquarto telas".

---

Adicione uma nova tela S17, "Detalhes do item", e não altere as outras telas além do necessário para abri-la.

**Quando abre:** ao clicar em qualquer destaque (HighlightCard) ou card do "Agora" no perfil público (S4). No quarto (S5), o painel de informações que já existe cumpre esse papel; só alinhe o conteúdo dele com o da S17.

**Formato:** diálogo centralizado a partir de 600px (largura máxima ~560px) e bottom sheet no celular. Fundo escurecido, botão fechar com `aria-label`, fecha com Esc e com o botão voltar do navegador (o estado fica na URL como `?item=<id>`).

**Conteúdo, na ordem:**
1. Capa na proporção da mídia (mesmas formas do design system: pôster, volume, vinil, game, DVD), com o sticker de categoria. Mantenha a personalidade (fita ou tachinha, leve inclinação) só na capa.
2. Título (até 2 linhas), ano e um subtítulo curto quando houver (diretor, autor, artista, estúdio).
3. A nota pessoal do dono, em Caveat sobre `paper`, se existir. Sem nota, nada aparece (não mostre placeholder para visitantes).
4. Origem, em `caption`: "Assistido em 3 out. 2026 no Letterboxd", "Lendo agora no Goodreads", "Ouvindo agora no Last.fm", ou "Escolhido à mão" para destaques manuais.
5. Links externos como botões secundários com o ícone `external-link` (Lucide): primeiro a plataforma de atividade quando existir (Letterboxd, AniList, Steam, Last.fm, Goodreads), depois a base de catálogo (TMDB, AniList, Open Library, IGDB, Deezer). Abrem em nova aba.
6. Para música, um botão de prévia de 30s (Deezer) é opcional; desenhe-o como variação.

**Estados (como props):** com nota e sem nota; item sincronizado e manual; carregando (capa no slot com a proporção real); item que não existe mais ("Esse item saiu do quarto.").

**Regras do design system:** controles sóbrios (sem inclinação nos botões), foco visível de 2px, contraste 4.5:1, nada de emoji, textos em pt-BR com "você".

Entregue desktop, tablet e celular, como nas outras telas, e atualize o README de `docs/design/telas` com a linha da S17.
