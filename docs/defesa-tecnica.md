# Defesa técnica: guia de estudo

Guia para responder, sem hesitar, sobre cada tecnologia usada no meuquarto. O professor liberou o uso de IA, mas cobra que você saiba explicar tudo o que está no projeto. Este guia complementa o `docs/poo-no-projeto.md`, que cobre as hierarquias de POO. Aqui ficam o resto: Spring, banco de dados, Docker, frontend e segurança.

Caminhos Java são relativos a `backend/src/main/java/com/meuquarto/`. Caminhos do frontend são relativos a `frontend/src/`.

## Como estudar com este guia

1. Comece pelos **fluxos**. Cada fluxo é um caminho completo, do clique na tela até a tabela no banco. Quem sabe contar três fluxos responde a maioria das perguntas, porque cada pergunta cai num ponto de algum deles.
2. Depois leia as tecnologias do **nível 1** até conseguir dizer as respostas em voz alta sem olhar.
3. Para os níveis 2 e 3, decore a resposta curta. Não precisa ir além dela.
4. Treine em voz alta. Ler não basta: a resposta tem que sair pronta.

Um formato que funciona para qualquer tecnologia: **o que é, por que está aqui, onde fica, o que aconteceria sem ela**.

## Mapa: o quanto saber de cada coisa

| Nível | O que significa | Tecnologias |
|---|---|---|
| 1. Saber bem | Vai ser perguntado. Explicar, mostrar no código, responder o "por quê". | Java e POO (ver `poo-no-projeto.md`), Spring Boot, JPA/Hibernate, PostgreSQL, Flyway, Docker e Docker Compose, API REST + frontend separado (Next.js) |
| 2. Duas ou três frases | O que é, por que está, onde fica. | Spring Security (login, sessão, CSRF, Argon2), Spring Session JDBC, Bean Validation, testes (JUnit e Testcontainers), cache (Caffeine), tarefa agendada (`@Scheduled`), e-mail (Mailpit), armazenamento S3 (RustFS), chamadas HTTP às plataformas, GitHub Actions |
| 3. Uma frase pronta | Infraestrutura que quase ninguém pergunta. | ArchUnit, gitleaks, CodeQL, Dependabot, limite de requisições, cabeçalhos de segurança, IP real atrás do proxy, virtual threads, Actuator, Cache Components do Next.js |

---

## Os fluxos

### Fluxo 1: entrar na conta (login)

1. **Tela** (`app/entrar/page.tsx`, `components/LoginForm.tsx`): o formulário manda e-mail e senha com `fetch` para `/api/auth/login`. Quem envia é o `lib/api-client.ts`, que antes pega o token CSRF e o coloca no cabeçalho `X-XSRF-TOKEN`.
2. **Proxy do Next** (`proxy.ts`): o navegador só conversa com o servidor do Next. Toda chamada a `/api/*` é repassada para a API Java (endereço em `API_URL`).
3. **Spring Security** (`shared/security/SecurityConfig.java`): confere o token CSRF e o limite de tentativas. A rota de login é uma das poucas liberadas sem sessão.
4. **Controller** (`identity/api/AuthController.java`, método `login` com `@PostMapping("/login")`): o `@Valid` valida o corpo da requisição e chama o serviço.
5. **Serviço** (`identity/application/Accounts.java`, método `signIn`): pede ao `AuthenticationManager` do Spring Security que confira a senha. A senha nunca é guardada; só o hash Argon2 (`identity/infra/IdentitySecurityConfig.java`).
6. **Sessão**: o controller cria a sessão e troca o id dela (proteção contra *session fixation*). A sessão é gravada no PostgreSQL pelo Spring Session (tabelas criadas pela migration `V4__create_spring_session.sql`). O navegador recebe o cookie `SESSION`, marcado `HttpOnly` (JavaScript não lê), `Secure` e `SameSite=Lax`.
7. **Banco**: o usuário vem da tabela `users` (`V3__create_users.sql`), lida pelo `identity/infra/UserRepository.java` (JPA).

Pergunta provável: "como a API sabe quem está chamando nas próximas requisições?" Resposta: pelo cookie `SESSION`. O Spring Session busca a sessão no PostgreSQL e o Spring Security coloca o usuário no contexto. Os controllers recebem o id com `@CurrentUserId` (`shared/security/CurrentUserId.java`), nunca do corpo da requisição.

### Fluxo 2: vincular uma conta (por exemplo, Letterboxd)

1. **Tela** (`app/painel/contas/page.tsx`, `components/linked/LinkCard.tsx`): o usuário digita o nome e clica em "Vincular Letterboxd". O `api-client.ts` faz `POST /api/linked-accounts` com o token CSRF.
2. **Proxy do Next** repassa para a API.
3. **Controller** (`media/api/LinkedAccountController.java`, método `link`): `@PostMapping`, `@Valid @RequestBody`, usuário vindo de `@CurrentUserId`. Responde `201 Created`.
4. **Serviço** (`media/application/LinkedAccounts.java`, método `link`), nesta ordem:
   - valida o formato do nome com as regras da hierarquia `ValidationRule` (POO, hierarquia 3);
   - pega no `ActivityRegistry` o provedor da plataforma, um `ActivityProvider` (POO, hierarquia 1), e chama `checkAccountExists`. É uma chamada HTTP para o Letterboxd, com tempo limite, feita **fora** de qualquer transação do banco;
   - grava com `accounts.saveAndFlush(...)`.
5. **Repositório** (`media/infra/LinkedAccountRepository.java`): interface que estende `JpaRepository`. O Spring Data gera a implementação.
6. **Banco**: tabela `linked_accounts`. Foi criada pela `V8__create_linked_accounts.sql` e alterada pela `V15` e pela `V16` (novas plataformas). Uma restrição `UNIQUE (profile_id, provider)` impede vincular a mesma plataforma duas vezes, mesmo com dois cliques ao mesmo tempo: o serviço captura a `DataIntegrityViolationException` e responde "já vinculada".
7. **Plataformas só de link** (Backloggd, Trakt, StoryGraph): só o formato é conferido e nenhuma chamada é feita (ADR 0023).

Este fluxo passa por quase tudo: Next, proxy, CSRF, Spring MVC, validação, POO, HTTP com timeout, JPA, Flyway e restrição do PostgreSQL. Vale saber contar de cabeça.

### Fluxo 3: destacar uma obra

1. **Tela** (`components/collection/DestaquesScreen.tsx` e `CollectionRow.tsx`): o botão "Destacar" chama `POST /api/highlights`.
2. **Controller** (`profile/api/HighlightController.java`) chama `ProfileHighlights.add`.
3. **Serviço** (`profile/application/ProfileHighlights.java`, método `add`, com `@Transactional`): busca o item na coleção do próprio usuário e a lista de destaques da categoria, e pede à lista que destaque o item.
4. **Regra de negócio no domínio** (`profile/domain/HighlightList.java`, método `highlight`): se a lista já tem `LIMIT = 4` destaques, lança `HighlightException` com o problema `HIGHLIGHT_LIMIT_REACHED`. É a regra BR-02, testada sem Spring em `BR02HighlightLimitTest`.
5. **Erro vira resposta HTTP** (`shared/api/DomainErrors.java`): a exceção (hierarquia `DomainException`, POO 4) vira `409 Conflict` com o código do erro, e a tela mostra a mensagem em português.
6. **Banco**: o `@Transactional` garante que tudo é salvo junto, ou nada é salvo.

Pergunta provável: "por que a regra está no domínio e não no controller?" Resposta: o controller só recebe e responde. A regra fica na classe que representa a lista de destaques, então vale para qualquer caminho que mexa nela e pode ser testada sem subir o Spring.

### Fluxo 4: abrir um perfil público

1. **Página** (`app/[slug]/page.tsx`): é um componente de servidor do Next. O HTML já sai pronto do servidor, o que ajuda buscadores e a prévia do link em redes sociais.
2. **Busca dos dados** (`lib/public-profile.ts`): o servidor do Next chama `GET /api/public/profiles/{slug}` direto na API.
3. **Controller** (`profile/api/PublicProfileController.java`): é uma das rotas liberadas sem login, só para leitura.
4. **Serviço** (`profile/application/PublicProfiles.java`): monta o perfil com o que está **no banco**, e nunca chama Letterboxd, AniList ou outra plataforma durante a montagem. As obras vêm da tabela `media_items`, uma cópia (snapshot) guardada na sincronização.
5. **Cache** (`profile/application/PublicProfileCache.java`): o perfil montado fica em memória por 60 segundos (Caffeine, até 2.000 perfis). É apagado na hora quando o dono muda algo.
6. **Partes que dependem de quem está vendo** (os botões do topo, por exemplo): ficam em `components/SessionActions.tsx` e `SessionSlots.tsx`, cada uma dentro de um `<Suspense>`. O resto da página pode ser pré-gerado (veja Cache Components no nível 3).

### Fluxo 5: sincronização automática

1. **Agendamento** (`media/application/SyncScheduler.java`): um método com `@Scheduled` roda a cada 15 minutos e pega até 20 contas que não sincronizam há 6 horas ou mais (ADR 0007).
2. **Serviço** (`media/application/SyncService.java`): para cada conta, chama o `ActivityProvider` da plataforma (polimorfismo: o serviço não sabe se é Letterboxd ou AniList).
3. **Plataforma**: a chamada HTTP tem tempo limite, e uma plataforma fora do ar não derruba as outras.
4. **Gravação** (`media/application/MediaSnapshots.java`): as obras vão para `media_items` e os itens entram na coleção.
5. **Botão "Sincronizar agora"**: usa o mesmo serviço, com um intervalo mínimo de 15 minutos por conta (BR-03).

---

## Nível 1: saber bem

### Java 21

- **O que é**: a linguagem do backend, na versão 21 (com suporte de longo prazo).
- **Por que aqui**: é a linguagem da disciplina, e o Spring Boot 4 pede no mínimo Java 17.
- **Onde**: `backend/pom.xml` (`<java.version>21</java.version>`).
- **Perguntas**:
  - "Por que 21 e não 17, como na aula?" A ideia é a mesma; 21 é a versão de suporte longo mais recente, e o Spring Boot 4 funciona com ela. Usamos `record` (classe imutável só com dados, por exemplo `LinkedAccounts.ProviderAvailability`) e `switch` com setas, que existem desde as versões 16 e 14.

### Spring Boot

- **O que é**: um framework que monta a aplicação Java com configurações prontas: servidor web embutido (Tomcat), injeção de dependências, acesso a banco, segurança.
- **Por que aqui**: é o que a disciplina usa, e evita configurar à mão o servidor, a conexão com o banco e a segurança.
- **Onde**: `ApiApplication.java` (classe com `@SpringBootApplication` e o `main`), `src/main/resources/application.yml` (configuração) e `pom.xml` (os *starters*).
- **Peças que você precisa nomear**:
  - `@RestController` + `@GetMapping`/`@PostMapping`: recebem requisições HTTP e devolvem JSON. Na aula, o `@Controller` devolvia uma página Thymeleaf; aqui devolvemos dados, e quem desenha a tela é o Next.js.
  - `@Service` e as classes de `application/`: os casos de uso.
  - **Injeção de dependência**: o Spring cria os objetos e os passa no construtor. Por exemplo, o `LinkedAccountController` recebe o `LinkedAccounts` sem dar `new`.
  - `@Transactional`: tudo dentro do método é salvo junto, ou nada é salvo.
  - `@Valid`: valida o corpo da requisição com as anotações dos DTOs (Bean Validation).
- **Perguntas**:
  - "Qual a diferença entre `@Controller` e `@RestController`?" O `@RestController` devolve dados (JSON) direto no corpo da resposta; o `@Controller` devolve o nome de uma página para um motor de templates como o Thymeleaf.
  - "O que é injeção de dependência?" Em vez de a classe criar o que usa, ela recebe pronto no construtor. Fica mais fácil testar e trocar peças.

### Organização do código Java (pacotes e camadas)

- **O que é**: cada assunto é um módulo (`identity`, `profile`, `media`, `room`), e cada módulo tem quatro pastas: `api/` (controllers e DTOs), `application/` (serviços), `domain/` (entidades e regras) e `infra/` (repositórios e chamadas externas). Os ADRs 0001 e 0014 registram a decisão.
- **Por que**: cada coisa tem um lugar previsível, e uma regra de negócio não fica espalhada em controller ou repositório.
- **Comparando com a aula**: na aula, tudo estava num pacote só (`com.trabalho.aula`). Aqui é o mesmo padrão controller, serviço, entidade e repositório, só que separado em pastas.
- **Perguntas**:
  - "O que vai no `domain`?" As classes do negócio e as regras, como a `HighlightList` com o limite de 4 destaques. O `domain` não depende do Spring nem do banco.
  - "Um módulo pode usar o repositório de outro?" Não. Só os serviços da pasta `application` do outro módulo. Um teste (ArchUnit, nível 3) falha se alguém fizer isso.

### JPA e Hibernate

- **O que é**: JPA é a especificação Java para mapear classes em tabelas; Hibernate é a implementação usada pelo Spring Data JPA. Uma classe com `@Entity` vira uma tabela, e cada objeto, uma linha.
- **Por que aqui**: menos SQL repetitivo. O `save`, o `findById` e as buscas pelo nome do método vêm prontos, e o código trabalha com objetos.
- **Onde**: as entidades ficam nas pastas `domain/` (por exemplo, `media/domain/LinkedAccount.java` e `profile/domain/HighlightList.java`) e os repositórios nas pastas `infra/` (por exemplo, `media/infra/LinkedAccountRepository.java`). São 11 entidades e 10 repositórios. Todo o acesso ao banco é JPA.
- **Configuração** (`application.yml`): `ddl-auto: validate` (o Hibernate só confere se as classes batem com as tabelas, e nunca cria nem altera tabela) e `open-in-view: false` (a conexão com o banco não fica aberta enquanto a resposta é montada).
- **O que saber explicar**:
  - `@Entity`, `@Table`, `@Id`, `@Column`: ligam a classe e os campos à tabela e às colunas.
  - `JpaRepository<Entidade, TipoDoId>`: interface do Spring Data; a implementação é gerada pelo Spring.
  - Método com nome de consulta, como `existsByProfileIdAndProvider`: o Spring entende o nome e gera o SQL.
  - `@Query`: quando o nome não basta, escreve-se a consulta em JPQL (parecido com SQL, mas sobre as classes). Há 21 no projeto, e só 2 são SQL puro (`nativeQuery = true`).
- **Perguntas**:
  - "Por que JPA e não JDBC, como na aula?" Os dois funcionam. Com JDBC escrevemos todo o SQL e lemos cada coluna à mão; com JPA o mapeamento objeto-tabela e as operações comuns vêm prontos. O próprio professor disse que JPA é mais fácil. O banco continua sendo controlado por nós, porque as tabelas são criadas pelo Flyway, e não pelo Hibernate.
  - "Onde está o SQL então?" Nas migrations do Flyway (estrutura das tabelas), nas `@Query` (consultas especiais) e no SQL que o Hibernate gera para o resto.
  - "O que acontece se a classe não bater com a tabela?" A aplicação nem sobe: o `ddl-auto: validate` acusa o erro na inicialização.

### PostgreSQL

- **O que é**: o banco de dados relacional do projeto.
- **Por que aqui**: é o que a disciplina usa, é robusto e gratuito, e tem o que usamos (restrições `CHECK` e `UNIQUE`, chaves estrangeiras).
- **Onde**: a conexão fica em `application.yml` (`spring.datasource.url`, `username`, `password`), o mesmo bloco da aula. A senha vem de variável de ambiente (`${DB_PASSWORD:}`), e não fica escrita no arquivo.
- **Onde ele roda**: num container do Docker Compose (`infra/docker-compose.yml`, serviço `postgres`, imagem `postgres:17.11-alpine3.24`). Nos testes, um PostgreSQL descartável sobe pelo Testcontainers.
- **Perguntas**:
  - "Por que a senha não está no `application.yml` como na aula?" O repositório é público. A senha fica no `.env`, que não vai para o git, e entra na aplicação como variável de ambiente.
  - "O que são as restrições no banco?" Regras que o próprio PostgreSQL garante, mesmo que o código falhe. Por exemplo, `UNIQUE (profile_id, provider)` em `linked_accounts` e o `CHECK` que só aceita os nomes de plataforma conhecidos.

### Flyway

- **O que é**: ferramenta que aplica scripts SQL versionados no banco. Cada mudança é um arquivo numerado (`V1__baseline.sql` até `V18__drop_store_tables.sql`). Ao subir, o Flyway roda só os arquivos que ainda não rodaram naquele banco e anota tudo na tabela `flyway_schema_history`.
- **Por que aqui**: o banco muda ao longo do projeto, e os dados existentes não podem ser perdidos. Exemplos: a V15 e a V16 mudaram a restrição de plataformas; a V17 adicionou colunas; a V18 apagou as tabelas da loja.
- **Onde**: `backend/src/main/resources/db/migration/`.
- **Comparando com a aula**: é o mesmo `schema.sql` da aula, só que versionado. O `CREATE TABLE IF NOT EXISTS` da aula não altera tabela que já existe; o Flyway sim, sem apagar os dados.
- **Regra do projeto**: nunca editar uma migration já aplicada. Mudança nova é sempre um arquivo novo.
- **Perguntas**:
  - "Por que não deixar o Hibernate criar as tabelas (`ddl-auto: update`)?" Porque ele gera o SQL sozinho, não cuida bem de restrições como as nossas e altera o banco de produção sem controle. Com o Flyway, todo SQL que roda no banco foi escrito e revisado.
  - "E se eu editar uma migration antiga?" O Flyway guarda a soma de verificação de cada arquivo aplicado e acusa a diferença ao subir. Por isso a regra é sempre criar uma migration nova.

### Docker e Docker Compose

- **O que é**: o Docker roda programas dentro de containers, ambientes isolados criados a partir de imagens. O Docker Compose sobe vários containers juntos, descritos num arquivo só.
- **Por que aqui**: um comando sobe o projeto inteiro, igual em qualquer máquina, sem instalar PostgreSQL, Java ou Node no computador. É também o mesmo jeito que o projeto vai rodar no servidor.
- **Onde**: `infra/docker-compose.yml`, `backend/Dockerfile` e `frontend/Dockerfile`. Comando:
  ```bash
  docker compose -f infra/docker-compose.yml --env-file .env up --build -d
  ```
- **Os serviços do compose**:
  - `postgres`: o banco. Os dados ficam no volume `pgdata`, que sobrevive quando o container é recriado.
  - `mailpit`: um servidor de e-mail de mentira. Os e-mails (recuperação de senha) aparecem em http://localhost:8025 em vez de ir para alguém.
  - `storage`: um armazenamento compatível com o S3 da Amazon (RustFS), para a foto do perfil. Os arquivos ficam no volume `storagedata`.
  - `api`: o backend Java, construído pelo `backend/Dockerfile`.
  - `web`: o frontend Next.js, construído pelo `frontend/Dockerfile`.
- **Detalhes que mostram que você entende**:
  - **Build em dois estágios** (`backend/Dockerfile`): um estágio com o JDK compila o projeto com o Maven; o estágio final só tem o JRE e o `.jar`, então a imagem final fica menor.
  - O container roda com um usuário comum (`USER app`), e não como administrador.
  - `HEALTHCHECK`: o Docker chama `/api/health` para saber se a API está de pé.
  - As portas são ligadas a `127.0.0.1`: só a sua máquina acessa, ninguém da rede.
  - `--build` reconstrói as imagens. Sem ele, o compose reaproveita a imagem antiga, e o código novo não entra.
  - `down -v` apaga também os volumes, ou seja, os dados do banco.
- **Perguntas**:
  - "Qual a diferença entre imagem e container?" A imagem é o molde (programa e dependências, só leitura); o container é uma execução dessa imagem.
  - "O que é um volume?" Uma pasta gerenciada pelo Docker que guarda os dados fora do container. Apagar o container não apaga o volume.
  - "O que o Docker faz que o PostgreSQL instalado não faria?" Sobe o banco, o e-mail de teste, o armazenamento, a API e o site juntos, com as mesmas versões em qualquer computador, com um comando só.
  - "Como a API acha o banco?" Pelo nome do serviço. Dentro da rede do compose, o endereço do banco é o host `postgres`, passado para a API como variável de ambiente.

### API REST + frontend separado (Next.js)

- **O que é**: o backend só responde JSON em rotas `/api/...`. As telas são feitas em Next.js (React com TypeScript), num projeto separado, a pasta `frontend/`. O professor disse que com React seria assim: pasta separada ou outro repositório.
- **Por que Next.js e não só React**: o perfil público precisa sair pronto do servidor, para aparecer em buscadores e na prévia de links em redes sociais. O Next renderiza no servidor; o React puro desenha só no navegador.
- **Onde**: `frontend/src/app/` (cada pasta é uma rota; por exemplo, `app/painel/contas/page.tsx` é a tela `/painel/contas`).
- **O proxy** (`proxy.ts`, ADR 0016): o navegador só conversa com o endereço do Next. O Next repassa `/api/*` para a API Java. Assim, o endereço da API nunca aparece no navegador e não há requisição entre sites diferentes, então não é preciso liberar CORS.
- **Perguntas**:
  - "O que é REST?" Um estilo de API em que cada recurso tem um endereço (`/api/linked-accounts`) e o verbo HTTP diz a ação: `GET` lê, `POST` cria, `DELETE` remove. A resposta é JSON.
  - "Por que dois projetos e não Thymeleaf?" Porque a interface tem bastante interação (o quarto em 3D, arrastar destaques, trocar o tema na hora), o que é mais natural em React. O professor aceitou essa separação.

---

## Nível 2: duas ou três frases

**Spring Security** (`shared/security/SecurityConfig.java`). Toda rota exige login, exceto uma lista pequena de rotas públicas: login, cadastro, recuperação de senha, perfil público, avatar e o teste de endereço livre. Quem não está logado recebe `401`. Ninguém consegue mexer em dados de outra pessoa, porque o dono vem sempre da sessão (`@CurrentUserId`) e nunca da requisição.

**Senha com Argon2** (`identity/infra/IdentitySecurityConfig.java`). A senha nunca é guardada, só um hash Argon2id com os parâmetros mínimos recomendados pela OWASP. Hash é de mão única: dá para conferir uma senha, mas não para descobrir a original (ADR 0012).

**Sessão no banco (Spring Session JDBC)**. A sessão de login fica numa tabela do PostgreSQL, e não na memória da API. Por isso o login continua valendo quando a API reinicia. O cookie `SESSION` é `HttpOnly`, então um JavaScript malicioso não consegue roubá-lo.

**CSRF** (`csrf.spa()` no `SecurityConfig`). Protege contra outro site mandar requisições em seu nome usando o seu cookie. A API manda um token num cookie (`XSRF-TOKEN`), o nosso frontend o lê e devolve num cabeçalho (`X-XSRF-TOKEN`). Um site de fora não consegue ler esse cookie, então a requisição dele é recusada.

**Bean Validation** (`@Valid` nos controllers, `@NotNull` e `@Size` nos DTOs; por exemplo, `identity/api/AccountController.java`). Confere o formato do que chega antes de chegar ao serviço. As regras de negócio mais ricas ficam na hierarquia `ValidationRule` (POO).

**Testes com JUnit e Testcontainers**. São cerca de mil testes, rodados com `./mvnw verify`. Testes de regra (como `BR02HighlightLimitTest`) rodam sem Spring. Testes que usam banco sobem um PostgreSQL de verdade num container descartável (Testcontainers, em `backend/src/test/java/com/meuquarto/support/TestcontainersConfiguration.java`), em vez de um banco falso. As plataformas externas são sempre simuladas nos testes.

**Cache em memória (Caffeine)** (`profile/application/PublicProfileCache.java`). Guarda o perfil público montado por 60 segundos, para não remontar a cada visita. É apagado na hora quando o dono muda algo.

**Tarefa agendada (`@Scheduled`)** (`media/application/SyncScheduler.java`). O Spring roda o método a cada 15 minutos para sincronizar as contas vinculadas (ADR 0007). Outro agendamento apaga os pedidos de troca de senha vencidos (`identity/application/PasswordReset.java`).

**E-mail** (`identity/infra/SmtpPasswordResetMailer.java`, `spring.mail` no `application.yml`). Só para a recuperação de senha. Localmente vai para o Mailpit; em produção, o mesmo código usará um provedor real, trocando só as variáveis `MAIL_*`.

**Armazenamento compatível com S3** (`shared/storage/S3ObjectStorage.java`, ADR 0009 e 0021). A foto do perfil não vai para o banco, vai para um armazenamento de arquivos. Localmente é o RustFS (no compose); em produção será o Cloudflare R2. O código fala a mesma língua (a API do S3) com os dois.

**Chamadas às plataformas** (as classes em `media/infra/`, como `LetterboxdProvider` e `AniListProvider`). Usam o cliente HTTP do Java com tempo limite e sem seguir redirecionamentos. Uma plataforma fora do ar não derruba as outras. Os nomes de usuário são validados e codificados antes de ir na URL.

**Imagem de prévia do link (Open Graph)** (`frontend/src/app/[slug]/opengraph-image.tsx`, `frontend/src/app/opengraph-image.tsx` e `frontend/src/lib/og-image.tsx`). É a imagem que aparece quando alguém cola o link do perfil no WhatsApp, no Discord ou no X. O Next gera um PNG de 1200x630 com o nome da pessoa, o endereço e a marca, usando o `ImageResponse` que já vem no próprio Next (`next/og`): a gente descreve a imagem com JSX e CSS simples e ele devolve o PNG. A imagem não usa capas nem foto, então o servidor nunca baixa imagem de plataforma nenhuma. Páginas sem dono (landing, termos, 404) usam a versão com o texto "Seu gosto num quarto só". A fonte é uma versão estática (TTF) da Big Shoulders Display, porque o `next/og` não lê os arquivos `woff2` do site.

**GitHub Actions** (`.github/workflows/ci.yml` e `security.yml`). A cada pull request, o GitHub roda os testes do backend, a verificação do frontend (lint, tipos, formatação, build) e o build das imagens Docker. Só entra no `main` o que passa.

---

## Nível 3: uma frase pronta

- **ArchUnit** (`backend/src/test/java/com/meuquarto/architecture/`): testes que conferem a organização do código; por exemplo, que o `domain` não depende do Spring e que um módulo não usa o repositório de outro.
- **gitleaks**: procura senhas e chaves esquecidas no histórico do git a cada PR.
- **CodeQL**: análise do GitHub que procura padrões de falha de segurança no código Java e TypeScript.
- **Dependabot** (`.github/dependabot.yml`): abre PRs sozinho quando sai versão nova de uma dependência.
- **Limite de requisições** (`shared/ratelimit/`): limita quantas vezes alguém tenta, por exemplo, logar num intervalo de tempo, para dificultar adivinhar senhas.
- **Cabeçalhos de segurança** (no `SecurityConfig` e no `frontend/next.config.ts`): instruções ao navegador, como não deixar a página ser aberta dentro de outra (proteção contra *clickjacking*) e de onde scripts podem ser carregados (CSP).
- **IP real atrás do proxy** (`server.tomcat.remoteip` no `application.yml`, ADR 0017): em produção a API fica atrás de outros servidores, e isso faz o limite de requisições contar o IP de quem acessa, não o do servidor intermediário.
- **Virtual threads** (`spring.threads.virtual.enabled`): uma linha que faz o Java usar threads leves para atender requisições; não muda nada no código.
- **Actuator** (`/api/health`): uma rota que responde se a API está de pé; o Docker usa para o `HEALTHCHECK`.
- **Cache Components do Next.js**: o Next guarda as partes fixas da página prontas e preenche só o que depende de quem está vendo; é por isso que as partes de sessão ficam dentro de `<Suspense>`.

---

## Perguntas gerais

**"Foi você que escreveu esse código?"** Resposta honesta, já que o uso de IA foi liberado: "Usei IA para escrever boa parte, mas cada escolha tem um motivo registrado nos ADRs (`docs/adr/`), e eu sei explicar o que cada parte faz e por quê." Depois, mostre um fluxo.

**"Por que tanta coisa num projeto de faculdade?"** "O projeto vai ser publicado na internet com contas de verdade, então segurança e banco versionado não são opcionais. Mas cada peça tem uma função; nada está aí só por estar." Tenha prontos dois exemplos do que foi tirado por não ser necessário: a loja com pagamento (ADR 0024) e a hierarquia `Plan`, que saiu junto com ela.

**"O que você faria diferente?"** Uma resposta boa é concreta e curta. Por exemplo: "Começaria com menos plataformas e uma só bem feita", ou "Teria definido as telas antes do código, como fizemos depois com os protótipos."

**"Mostra no código onde está X."** Use as seções acima: cada item diz o arquivo. Treine abrir os arquivos dos fluxos 1 a 3 sem procurar.
