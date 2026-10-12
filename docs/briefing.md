# Briefing: Meu Quarto, perfil agregador de mídias (P2 de POO)

Documento de contexto gerado a partir de uma sessão de brainstorm. Serve de ponto de partida para refinar requisitos, arquitetura e, depois, o layout.

**Nome:** **meuquarto** (decidido). A metáfora: o quarto reúne todos os gostos da pessoa. Foco no público brasileiro, que já é grande o suficiente por enquanto; código, repositório e documentação técnica podem ficar em inglês. Ainda falta checar domínio, @ nas redes e INPI.

**Horizonte:** além da P2, se o projeto der certo pode virar o **TCC**. Por isso precisa ser escalável e bem estruturado desde o início (não em nível de big tech, mas pronto para crescer sem reescrever).

---

## 1. Contexto acadêmico

**Disciplina:** Programação Orientada a Objetos (Fatec Baixada Santista)
**Avaliação:** P2

### Requisitos do professor
- Trabalho com front-end + back-end
- Individual (o permitido era dupla na mesma faixa; será feito sozinho)
- Tema livre
- Mínimo de 3 tabelas
- Repositório no GitHub
- CRUD como funcionalidade mínima
- Pelo menos uma regra de negócio
- Projeto hospedado

### Stack obrigatória
- **Back-end:** Java, provavelmente Spring Boot (ainda não ensinado em aula)
- **Banco:** PostgreSQL
- **Front-end:** a definir. A exigência de Java vale para o back-end; o front roda no navegador e pode usar JavaScript/TypeScript normalmente (ex.: React + Three.js). **Confirmar com o professor** se o front também precisa ser em Java (ex.: Thymeleaf); mesmo nesse caso, é possível carregar Three.js na página.

### Desafio extra (opcional, vale nota extra na P1)
Transformar o projeto em SaaS, monetizar e provar ao professor que gerou algum dinheiro. Implica custo de VPS e domínio. Decisão atual: fazer a P2 e tentar o desafio.

---

## 2. Conceito do produto

Um "link-in-bio" focado em gosto pessoal por mídia. Em vez de uma lista de links (Linktree), o perfil exibe **destaques** de filmes, séries, animes, mangás, livros, games e música, em um só lugar e fácil de compartilhar.

**Premissa central:** o usuário continua usando as plataformas onde já tem histórico (Letterboxd, MyAnimeList etc.). O produto não substitui essas plataformas, ele agrega e exibe.

**Origem da ideia:** o autor já faz isso manualmente no próprio vault do Obsidian, importando dados do Letterboxd e do MyAnimeList.

**Escopo distinto de outro projeto pessoal do autor (My Media Catalogue):** aquele é privado, para catalogar tudo e acompanhar lançamentos. Este é social, para compartilhar interesses.

---

## 3. Concorrência

### Direto
- **YourShelf** (lançado em mar/2026, dev solo da Espanha, gratuito): link-in-bio de mídia com top 4 em filmes, séries, livros, games e música, itens buscados em bases como TMDB, Spotify, Hardcover e IGDB, 60+ temas, fontes e fundos. Pelo que foi possível observar, a curadoria é **manual**, sem sincronização com os perfis do usuário.

### Indiretos (substituem as plataformas em vez de agregar)
- **Nooklet**: "Letterboxd para tudo", com import de Goodreads, IMDb, Letterboxd, AOTY e MAL
- **Passport**, **Wecult**, **JoyHob**: trackers unificados multimídia
- **Linktree / Beacons**: link-in-bio genérico

### Diferenciais pretendidos
1. **Sincronização real** com os perfis do usuário onde existe plataforma dominante (bloco "agora", destaques que se atualizam sozinhos)
2. **Anime e mangá como cidadãos de primeira classe** (MAL/AniList), o que os link-in-bio concorrentes não priorizam
3. **Mercado brasileiro**: português, preço em real, pagamento via Pix

---

## 4. Modelo de dados das mídias: híbrido

Decisão tomada: separar dois papéis.

- **Fonte de atividade** (onde o usuário registra o que consome): só integrar plataformas realmente populares. Integrar algo de nicho quebra a premissa.
- **Catálogo/metadados** (pôster, sinopse, ano): invisível ao usuário, pode vir de qualquer base (TMDB, IGDB, Deezer...).

**Regra do produto:** toda categoria funciona no modo manual (usuário busca no catálogo e fixa destaques). Algumas categorias ganham sincronização automática quando o usuário já usa a plataforma correspondente.

### Integrações por categoria

| Mídia | Sincronização (fonte de atividade) | Catálogo para modo manual | Observações |
|---|---|---|---|
| Filmes | Letterboxd via RSS público (~50 itens mais recentes do diário) | TMDB | API oficial do Letterboxd é só por solicitação e não atende projetos pessoais nem que recriem recursos pagos deles. RSS é oficial e público. |
| Séries | Nenhuma (sem plataforma dominante; TV Time anunciou encerramento em jul/2026) | TMDB | Só modo manual. |
| Anime | MyAnimeList (API v2, listas públicas com client ID) e/ou AniList (GraphQL público) | MAL / AniList | |
| Mangá | MyAnimeList / AniList | MAL / AniList | Mesma integração do anime. |
| Livros | Goodreads via RSS das estantes | Hardcover (API GraphQL) ou Open Library | API do Goodreads fechada para chaves novas; RSS é o elo mais frágil. |
| Games | Steam Web API (perfis públicos) | IGDB | |
| Música | Last.fm opcional ("ouvindo agora", tops por período, faixas amadas) | Deezer (busca pública, sem login, com capa e prévia de 30s); MusicBrainz + Cover Art Archive como reserva | Integração com playlists **descartada**. Basta "artistas/músicas favoritos" manual. Last.fm exige scrobble ativo (nativo no Spotify; via extensão/app de terceiros no YT Music e Apple Music). |

**Fallback para plataformas sem API** (Skoob, StoryGraph, Backloggd etc.): import por CSV, sem scraping.

**Decisão:** apresentar o projeto com as integrações já "prontas", cobrindo todas as categorias, mesmo que outros recursos fiquem para o roadmap.

**Pendência:** revisar os termos de uso de cada API, especialmente sobre uso em projeto monetizado e exibição de imagens (TMDB e outras podem exigir licença comercial).

### Sugestão de design orientado a objetos
Interface comum (ex.: `MediaProvider`) com uma implementação por plataforma (padrões Strategy/Adapter), todas convertendo para um modelo comum (ex.: `MediaItem`). Adicionar uma plataforma nova = criar uma classe nova. Bom ponto para demonstrar POO na apresentação.

---

## 5. Feature "Ver quarto"

Visualização do perfil como um quarto, com as mídias virando objetos. É uma ação ("ver quarto"), **não** a exibição padrão do perfil, porque exige carregamento maior.

- **Câmera:** fixa, isométrica, vista de cima (estilo diorama). Câmera ortográfica, sem controle livre, cena pequena.
- **Mídias como objetos:**
  - Pôsteres na parede: filmes e séries
  - Estante: livros e mangás
  - Toca-discos/vinis: música
  - TV/console: games
  - Quadro/mural: animes
- Os pôsteres são texturas geradas a partir dos destaques que o perfil já tem (mesmos dados, outra forma de exibir).
- **Tecnologia:** Three.js (WebGL por baixo) ou react-three-fiber. Alternativa mais leve: arte isométrica 2D com slots (estilo Habbo), com possibilidade de migrar para 3D depois. Decisão em aberto.
- **Assets iniciais:** kits low-poly do Kenney (kenney.nl), licença CC0, liberados para uso comercial.
- **Monetização:** é o principal espaço para cosméticos pagos originais (móveis, piso, cor de parede, plantas, iluminação dia/noite etc.), sem depender de propriedade intelectual de terceiros.
- **Entidades e regras que o quarto adiciona:** catálogo de itens, inventário do usuário, layout do quarto, compras. Regras como: cada slot aceita um item, item premium só para quem comprou, limite de objetos por parede.
- **Risco de escopo:** 3D pode consumir tempo demais. Tratar como feature que não pode travar a entrega da P2, já que a avaliação foca em back-end, CRUD e regras.

---

## 6. Monetização

**Modelo:** freemium. Funcionalidades básicas grátis; customizações avançadas pagas.

### Restrição de direitos autorais
Não é possível vender cosméticos temáticos de franquias (ex.: fundo animado de The Witcher). A Steam faz isso porque tem contrato com as publishers. Princípio adotado: **vender o efeito, não a obra**.

### Cosméticos e recursos pagos possíveis
- Bordas/molduras de foto de perfil, temas, layouts, fundos abstratos e animados **originais**
- Upload de fundo próprio (responsabilidade do usuário via termos de uso)
- Fundo dinâmico gerado a partir dos pôsteres dos próprios destaques (ex.: mosaico desfocado), sujeito aos termos das APIs
- Domínio próprio, estatísticas de visitas
- Referências de estilo: vitrines do perfil Steam, nostalgia de perfil customizável estilo MySpace

---

## 7. Escalabilidade (requisito, pensando em TCC)

Objetivo: crescer sem reescrever. Sugestões a validar no refinamento (não são decisões fechadas):

- Monólito modular no back-end, com módulos bem separados (perfil, integrações, quarto, pagamentos), em vez de microsserviços
- Integrações isoladas atrás de uma interface comum (`MediaProvider`), adicionando plataformas sem mexer no resto
- Sincronização assíncrona: começar com jobs agendados (`@Scheduled`), com caminho para fila se o volume crescer
- Cache das respostas das APIs externas e respeito aos limites de cada uma
- Imagens (texturas, uploads, cards) em object storage, não no banco
- Migrations versionadas (Flyway ou Liquibase)
- Documentação da API (OpenAPI/Swagger)
- Docker para ambiente e deploy; CI com testes automatizados
- Documentação de decisões de arquitetura desde o início (útil para o TCC)

---

## 8. Ideias de funcionalidades

### Candidatas a MVP
- Perfil público com slug único
- Integrações de todas as categorias (seção 4)
- Destaques fixados por categoria
- Bloco "Agora" (assistindo/lendo/ouvindo), alimentado pela sincronização
- Pelo menos um recurso pago com pagamento via Pix

### Roadmap (diferenciais fortes para depois do MVP)
- **Compatibilidade de gosto** entre dois perfis ("vocês são 78% compatíveis"), com potencial viral
- **Widgets embutíveis** (README do GitHub, blog, Discord)
- **Cards para stories** e preview bonito ao colar o link (Open Graph)
- **Retrospectiva anual multimídia** (forte candidata a recurso pago)
- **"Me recomenda algo"**: visitantes sugerem obras, dono aceita/recusa, aceitas viram lista pública
- **Coleções mistas** entre mídias (ex.: "cyberpunk": filme + anime + livro + álbum)
- Nota pessoal em cada destaque ("por que está aqui")
- Controle de spoiler e conteúdo adulto
- IA apenas como recurso extra, nunca como core do produto

### Regras de negócio candidatas
- Intervalo mínimo entre sincronizações por conta vinculada
- Limite de destaques e de integrações no plano grátis
- Slug de perfil único
- Deduplicação de itens vindos de fontes diferentes
- Recursos premium bloqueados para plano grátis
- (Se houver "me recomenda algo") moderação, limite por visitante, bloqueio

### Tabelas candidatas (rascunho, a refinar)
usuários, contas vinculadas, itens de mídia, destaques, planos/assinaturas, pagamentos, itens do quarto (catálogo), inventário, layout do quarto

---

## 9. Validação sugerida
Perguntar a 15 a 20 pessoas (colegas da Fatec, amigos) quais apps de mídia usam, para confirmar as integrações prioritárias. O resultado pode virar um slide da apresentação.

---

## 10. Fluxo de trabalho

Objetivo: desenvolvimento o mais organizado possível, com entregas incrementais por feature.

### Etapas
1. **Planejamento (Claude Code, skill grill-me)**, a partir deste briefing. Entregáveis:
   - Requisitos funcionais e não funcionais
   - Arquitetura e modelo de dados
   - Escopo do MVP x roadmap
   - **Lista de telas e fluxos de navegação** (insumo para o design)
   - Backlog inicial (épicos, features, issues)
2. **Design (Claude Design)**, a partir do planejamento:
   - Design system (cores, tipografia, espaçamento, componentes)
   - Telas definidas no planejamento
3. **Desenvolvimento (Claude Code)**, a partir do planejamento + design:
   - Uma feature por vez, seguindo o backlog
   - Cada entrega funcional, testada e publicada antes da próxima

### Organização no GitHub
- **Backlog:** GitHub Issues + GitHub Projects (quadro com colunas tipo Backlog, A fazer, Em andamento, Em revisão, Feito)
- **Hierarquia:** épicos (ex.: Integrações, Perfil, Quarto, Pagamentos) > features > issues/tarefas
- **Entregas:** milestones por incremento (ex.: MVP do perfil, integrações, quarto, monetização)
- **Labels:** tipo (feature, bug, chore, docs), área (backend, frontend, infra), prioridade
- **Fluxo de código:** uma branch por issue, PR vinculado à issue, template de PR e de issue
- **GitHub Actions:** CI (build, testes, lint) em todo PR, deploy na VPS ao fazer merge na main, e automações do Project (ex.: issue nova entra no backlog, PR mergeado move o card para Feito)
- **Documentação no repo:** pasta `docs/` com este briefing, requisitos, decisões de arquitetura (ADRs) e o design system

## 11. Próximos passos
1. **Claude Code (skill grill-me):** levantar requisitos funcionais e não funcionais, definir arquitetura (back-end Spring Boot, front-end, sincronização, hospedagem em VPS), modelo de dados, escopo final do MVP, lista de telas e backlog inicial
2. **Claude Design:** design system e telas
3. **Claude Code:** desenvolvimento incremental por feature

## 12. Questões em aberto
- Front-end: qual stack? (confirmar com o professor se pode ser JS/TS)
- Quarto: 3D (Three.js) ou isométrico 2D no início?
- Quarto entra na versão da P2 ou fica para o TCC?
- Arquitetura de sincronização: jobs agendados, fila, cache?
- Gateway de pagamento para Pix (Mercado Pago, Stripe, outro)?
- Hospedagem: qual VPS, domínio, deploy
- Validar disponibilidade de "meuquarto": domínio, redes, INPI
- Quais itens do roadmap entram na versão apresentada
- Revisão dos termos de cada API para uso comercial
