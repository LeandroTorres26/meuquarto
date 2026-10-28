# POO no projeto: guia de estudo

Guia para a entrega do P2 de Programação Orientada a Objetos. O professor pergunta, e você precisa explicar cada hierarquia linha por linha. Para cada tópico da disciplina abaixo você encontra onde ele está neste projeto, uma explicação com o vocabulário das aulas e perguntas prováveis com respostas curtas.

Este arquivo é mantido junto com o código: quem criar ou mudar uma hierarquia atualiza o guia no mesmo PR (regra no `CLAUDE.md`). A decisão de projeto está em `docs/adr/0018-own-object-oriented-hierarchies.md`.

Todos os caminhos são relativos a `backend/src/main/java/com/meuquarto/`. Testes ficam em `backend/src/test/java/com/meuquarto/`.

## Mapa rápido das 5 hierarquias

| # | Hierarquia | Situação |
|---|---|---|
| 1 | `ActivityProvider` (interface) -> `RssActivityProvider` (abstrata) -> `LetterboxdProvider`, `GoodreadsProvider` (final); `AniListProvider`, `SteamProvider`, `LastFmProvider` implementam direto | planejado nas issues #28 a #34 |
| 2 | `CatalogProvider` (interface) -> `HttpCatalogProvider` (abstrata, template method) -> `TmdbCatalog`, `AniListCatalog`, `OpenLibraryCatalog`, `IgdbCatalog`, `DeezerCatalog` | `CatalogProvider`, `HttpCatalogProvider` e `TmdbCatalog` feitos (issue #21); os outros quatro na issue #22 |
| 3 | `Plan` (interface) -> `FreePlan`, `PackPlan` | feito (issue #24); `PackPlan` só passa a ser usado quando houver compra (issue #39) |
| 4 | `ValidationRule<T, P>` (interface genérica) -> `LengthRule<T, P>` (abstrata) -> `SlugLengthRule`, `PasswordLengthRule`; mais cinco regras que implementam a interface | feito |
| 5 | `DomainException` (abstrata) -> `AccountRuleException`, `InvalidSlugException`, `SlugUnavailableException`, `TooManyAttemptsException`, `CollectionException`, `HighlightException`, `PublicProfileException` | feito |

Opcional depois (issue #43): `RoomObject` (abstrata) -> `Poster`, `Shelf`, `RecordPlayer`, `Tv`.

## Regras da coleção (issue #23)

Não é uma hierarquia nova (o ADR 0018 não prevê uma), são regras de negócio em classes comuns, e é isso que o professor também quer ver: encapsulamento e composição.

- Onde: `profile/domain/CollectionItem.java` (entidade com as regras), `ItemSource.java` (objeto de valor: `MANUAL` ou o nome da plataforma), `Note.java` (limpa e valida a nota), `CollectionSort.java`, `CollectionException.java`; serviço em `profile/application/ProfileCollection.java`. Testes: `profile/domain/CollectionItemTest.java` (sem Spring), `profile/CollectionApiTests.java`, `profile/CollectionSyncTests.java`.
- Regras: BR-07 (um item por obra: adicionar de novo devolve o mesmo item e reexibe se estava oculto), FR-09 (item que veio de sync não pode ser removido, só ocultado; um sync novo nunca reexibe um item oculto), FR-10 (nota de até 140 caracteres, aparada, em texto puro).
- Explicação: o `CollectionItem` não tem setters públicos; as mudanças são métodos com nome de regra (`addAgainByHand`, `seenBySync`, `hide`, `ensureRemovable`). Quem chama não faz `if` sobre as origens, pergunta `isRemovable()`.
- Perguntas:
  - Por que o item guarda `mediaItemId` e não um `MediaItem`? O módulo `profile` não pode importar o domínio do `media` (ADR 0001 e 0014, conferido por ArchUnit); a ligação é por id.
  - Por que `ItemSource` é um `record`? É um valor: duas origens com os mesmos campos são a mesma origem.

## O que NÃO conta (não cite na entrega)

O professor disse que recursos do Spring Boot não contam como POO do projeto. Não use estes como exemplo de herança, interface ou polimorfismo:

- `AccountPrincipal` implementar `UserDetails` (identity/infra): é interface do Spring Security.
- `UserRepository` e `ProfileRepository` estenderem `JpaRepository` (ou similar): é herança de interface do Spring Data.
- Anotações: `@Service`, `@RestController`, `@Entity`, `@Transactional`, `@ExceptionHandler`, `@RestControllerAdvice`. São metadados do framework, não herança nem polimorfismo.
- `OncePerRequestFilter` e qualquer classe de `shared/ratelimit` ou `shared/security` que estenda ou implemente tipos do Spring.
- Injeção de dependência (o Spring escolhe o objeto): não é a mesma coisa que polimorfismo escrito por você.

Regra de bolso: se o `extends` ou `implements` aponta para um tipo que você não escreveu (`org.springframework...`, `jakarta...`), não é exemplo. Os exemplos válidos são os das hierarquias 4 e 5 abaixo, mais `RuntimeException` como superclasse da `DomainException`, que é a base normal da linguagem (e mesmo assim o foco é a `DomainException`, que é nossa).

Uma observação honesta: `Slug` e `PasswordCandidate` são `record`, assunto que pode não ter sido visto em aula. Se perguntarem, diga que `record` é uma classe com campos `final`, construtor e getters gerados pelo compilador. Os exemplos centrais deste guia usam classes comuns.

---

## Tópicos básicos

### Classes e objetos

- Onde: `identity/domain/LoginThrottle.java` (classe com estado), `identity/domain/PasswordPolicy.java`.
- Explicação: `LoginThrottle` guarda o estado (`failures`, `maxFailures`, `window`) e tem métodos que agem sobre ele (`recordFailure`, `retryAfter`). Cada `new LoginThrottle(...)` é um objeto com seus próprios dados, como o `Cachorro` das primeiras aulas.
- Perguntas:
  - Qual a diferença entre classe e objeto? A classe é o molde (`PasswordPolicy`); o objeto é uma instância criada com `new PasswordPolicy(...)`.
  - Quantos objetos de `PasswordPolicy` existem? Um, criado na configuração e usado por quem precisa; cada um teria sua própria lista de regras.

### Encapsulamento

- Onde: `shared/domain/DomainException.java` (campos `private final code` e `kind`, acesso por `code()` e `kind()`); `identity/domain/AccountRuleException.java` (campo `rule` privado).
- Explicação: os campos são `private`, ninguém de fora altera o código de um erro depois de criado. Só os métodos públicos expõem o valor, igual ao `nome` do `Animal` da aula 9 (privado, com `getNome()`).
- Perguntas:
  - Por que os campos são privados? Para o objeto controlar seu próprio estado; um erro com código trocado no meio do caminho quebraria a resposta da API.
  - Por que `code()` e não `getCode()`? Convenção do projeto (a mesma de `Slug.value()`); o conceito é o mesmo do getter.

### Construtores

- Onde: `shared/domain/LengthRule.java` (construtor `protected LengthRule(int min, int max)` valida os limites); `identity/domain/AccountRuleException.java` (construtor recebe a `Rule` e chama `super`).
- Explicação: o construtor garante que o objeto nasce válido. Em `LengthRule`, `min` negativo ou `max < min` lança `IllegalArgumentException`, então nunca existe uma regra de tamanho impossível. É o mesmo papel do construtor `Animal(String nome)` da aula 9.
- Perguntas:
  - Para que serve o construtor? Para inicializar os campos e impedir objetos inválidos.
  - Por que o construtor da `DomainException` é `protected`? Porque a classe é abstrata: só as subclasses o chamam, via `super(...)`.

### this

- Onde: `shared/domain/LengthRule.java` (`this.min = min; this.max = max;`); `identity/application/TooManyAttemptsException.java` (`this.retryAfter = retryAfter;`).
- Explicação: o parâmetro e o campo têm o mesmo nome; `this.min` é o campo do objeto, `min` sozinho é o parâmetro. Mesmo uso do `this.nome = nome` do `Animal`.
- Pergunta: o que acontece se eu esquecer o `this`? Atribuo o parâmetro a ele mesmo e o campo fica com o valor padrão (`0`), sem erro de compilação.

### static

- Onde: `shared/domain/ValidationRule.java` (`static <T, P> Optional<P> firstProblem(...)`); `profile/domain/Slug.java` (`Slug.of`, `Slug.normalize`, `Slug.check`, constantes `MIN_LENGTH` e `MAX_LENGTH`).
- Explicação: membros `static` pertencem à classe, não a um objeto. `Slug.normalize(raw)` não precisa de um `Slug` pronto (ele serve justamente para criar um), como o `Maybe.just(...)` da correção da P1.
- Perguntas:
  - Por que `normalize` é static? Porque roda antes de existir um `Slug`.
  - Dá para chamar `firstProblem` sem criar objeto? Sim: `ValidationRule.firstProblem(regras, valor)`, direto na interface.

### Sobrecarga (overloading)

- Onde: `profile/domain/Profile.java` e `identity/domain/User.java`: cada uma tem dois construtores, `Profile()` (protegido, para o JPA) e `Profile(long userId, Slug slug)`.
- Explicação: mesmo nome, listas de parâmetros diferentes (`[]` e `[long, Slug]`), igual ao `Foo.algo()` / `algo(int)` da aula 8. O compilador escolhe pela lista de parâmetros, não pelo tipo de retorno. Honestidade: o construtor sem argumentos existe por exigência do JPA; o exemplo ainda vale como sobrecarga, mas se o professor pedir um caso "só nosso", cite `AccountRuleException` e `InvalidSlugException`, que fazem `super(...)` com listas diferentes.
- Perguntas:
  - O que identifica um método na sobrecarga? O nome mais a lista de parâmetros (a aula usa a notação `LP = [int, String]`).
  - Posso sobrecarregar mudando só o retorno? Não, não compila.

### Enums

- Onde (kernel compartilhado): `shared/domain/MediaCategory.java` (as sete categorias), usado por `media`, `profile` e pelos futuros destaques e sync. Ficou em `shared/domain` para os módulos concordarem no vocabulário sem importar o domínio um do outro (ADR 0019). Também: `profile/domain/CollectionSort.java` (`RECENT`, `TITLE`, `YEAR`, com o método estático `parse`) e `profile/domain/CollectionException.java` (enum `Problem`, cada valor carrega o seu `ErrorKind`; `ErrorKind` ganhou `NOT_FOUND` e `UNAVAILABLE`).

- Onde: `shared/domain/ErrorKind.java` (`INVALID`, `CONFLICT`, `TOO_MANY`); `identity/domain/AccountRuleException.java` (enum `Rule`); `profile/domain/Slug.java` (enum `Problem`).
- Explicação: um enum é um conjunto fechado de valores. `ErrorKind` classifica o erro sem falar de HTTP; quem traduz para 400, 409 ou 429 é a camada de API (`shared/api/DomainErrors.java`). Como o `TipoProduto` das aulas 5 e 6.
- Perguntas:
  - Por que enum e não `String`? O compilador impede valores inválidos e o `switch` avisa se faltar um caso.
  - Por que o domínio não usa `HttpStatus` direto? Para o domínio não depender do Spring nem de HTTP.

### Composição

- Onde (coleção): `profile/domain/CollectionItem.java` tem um `Set<ItemSource>` (as origens do item) e guarda o id do item de mídia, não o objeto: o `profile` não pode importar o domínio do `media`, então a ligação é por id e quem monta a tela é `profile/application/ProfileCollection.java`, que pergunta a `media/application/MediaSummaries.java`. `ProfileCollection` também tem (composição) o repositório, o `MediaSnapshots` e o `MediaSummaries`.

- Onde: `identity/domain/PasswordPolicy.java` (tem uma `List<ValidationRule<PasswordCandidate, Rule>>`); `profile/domain/Slug.java` (tem a lista `RULES`); `identity/domain/PasswordCandidate.java` (tem uma `EmailAddress`).
- Explicação: composição é "tem um" / "tem vários". `PasswordPolicy` não herda das regras, ela as possui e delega. Como o `Carrinho` que tem `Produto`s na aula 5.
- Perguntas:
  - Qual a diferença entre composição e herança? Composição é "tem um" (`PasswordPolicy` tem regras); herança é "é um" (`SlugLengthRule` é uma `LengthRule`).
  - Por que não fazer `PasswordPolicy extends LengthRule`? Uma política não é uma regra de tamanho; ela usa várias regras.

### ArrayList / List

- Onde: `identity/domain/PasswordPolicy.java` (`List.of(...)`); `profile/domain/Slug.java` (`List<ValidationRule<String, Problem>> RULES`); `shared/domain/ValidationRule.java` (`firstProblem` recebe uma `List`). Em teste, `ArrayList` explícito em `shared/domain/DomainExceptionTest.java`.
- Explicação: a lista guarda as regras na ordem em que devem ser perguntadas. O tipo da variável é a interface `List` e o objeto concreto pode ser qualquer lista, como o `ArrayList<Animal>` do PetShop. `List.of` cria uma lista imutável.
- Pergunta: por que a ordem importa? `firstProblem` devolve o primeiro problema; o tamanho vem antes dos caracteres, então `"a"` é `TOO_SHORT` e não `INVALID_CHARACTERS`.

### Generics

- Onde: `shared/domain/ValidationRule.java` (`ValidationRule<T, P>`), `shared/domain/LengthRule.java` (`LengthRule<T, P>`).
- Explicação: dois parâmetros de tipo, como `Dupla<S, W>` da aula 7: `T` é o que se valida (`String` para slug, `PasswordCandidate` para senha) e `P` é o problema devolvido (`Slug.Problem` ou `AccountRuleException.Rule`). `Optional<P>` também é genérico e faz o papel do `Maybe<T>` da correção da P1: ou tem valor, ou não tem.
- Perguntas:
  - Por que genérico e não `Object`? Sem cast e com checagem em tempo de compilação: uma regra de slug só aceita `String`.
  - O que o `Optional<P>` evita? Devolver `null` para dizer "sem problema".
  - O método `firstProblem` é genérico também? Sim, `<T, P>` antes do tipo de retorno, igual ao `static <T> Maybe<T> just(...)`.

---

## Herança e polimorfismo

### Herança (extends, super, protected, @Override)

- Onde: `shared/domain/DomainException.java` e as quatro subclasses (`identity/domain/AccountRuleException.java`, `profile/domain/InvalidSlugException.java`, `profile/application/SlugUnavailableException.java`, `identity/application/TooManyAttemptsException.java`). Também `shared/domain/LengthRule.java` com `profile/domain/SlugLengthRule.java` e `identity/domain/PasswordLengthRule.java`.
- Explicação: `AccountRuleException extends DomainException`: ganha `code()`, `kind()` e `retryAfter()` sem reescrever. O construtor da subclasse chama `super(rule.name(), ...)`, que executa o construtor da classe de cima, igual ao `super(nome)` do `Cachorro` na aula 9. `@Override` aparece em `TooManyAttemptsException.retryAfter()` (troca o comportamento padrão "vazio") e nos métodos `lengthOf`, `tooShort` e `tooLong` das regras de tamanho. `protected` aparece no construtor da `DomainException` e nos métodos que `LengthRule` deixa para as subclasses.
- Perguntas:
  - O que o `super(...)` faz nesse construtor? Chama o construtor da superclasse (`DomainException`) passando o código e o tipo do erro; é obrigatório porque a superclasse tem construtor explícito (o comentário da aula 9: construtor explícito gera obrigação nas subclasses).
  - Para que serve o `@Override`? O compilador confere que o método realmente sobrescreve algo; se eu errar o nome, ele reclama.
  - Por que `protected` e não `public` ou `private`? `private` esconderia das subclasses; `public` abriria para qualquer um. `protected` deixa só a hierarquia usar. (A aula 9 diz que usar `protected` em campo não é recomendado; por isso os campos continuam `private` e só os métodos e o construtor são `protected`.)
  - Qual a vantagem de herdar? O `DomainErrors` trata os quatro erros com um único código, em vez de quatro.

### Classe abstrata

- Onde: `shared/domain/DomainException.java` e `shared/domain/LengthRule.java`.
- Explicação: classe abstrata não pode ser instanciada (`new DomainException(...)` não compila) e serve de base. Como o `Animal` da aula 9: um "animal genérico" não existe, só o cachorro e o gato. `LengthRule` tem método abstrato de verdade (`lengthOf`, `tooShort`, `tooLong`): a subclasse é obrigada a implementar. O método concreto `check` usa os abstratos, o padrão chamado template method.
- Perguntas:
  - Por que `LengthRule` é abstrata? Ela sabe o algoritmo (menor que o mínimo, maior que o máximo), mas não sabe medir o valor nem qual problema devolver; deixa isso para `SlugLengthRule` e `PasswordLengthRule`.
  - Por que classe abstrata e não interface aqui? Porque `LengthRule` guarda estado (`min`, `max`) e código pronto (`check`); interface não guarda campos de instância.
  - Por que `DomainException` é abstrata se não tem método abstrato? Porque um erro de domínio genérico não faz sentido; sempre se lança um específico. Uma classe abstrata pode não ter métodos abstratos.
  - Por que não um método abstrato `code()` na `DomainException`? Cada subclasse teria que implementar só para devolver uma constante; passando o código ao `super(...)`, ele é dito uma vez, no construtor.

### final

- Onde: `final class` em todas as folhas: `AccountRuleException`, `InvalidSlugException`, `SlugUnavailableException`, `TooManyAttemptsException`, `SlugLengthRule`, `SlugCharactersRule`, `SlugHyphenRule`, `SlugReservedRule`, `PasswordLengthRule`, `CommonPasswordRule`, `PasswordEqualsEmailRule`, `PasswordPolicy`. E `public final Optional<P> check(...)` em `LengthRule`.
- Explicação: classe `final` não pode ser estendida, como o `Cachorro` da aula 9 (`public final class Cachorro`). Método `final` não pode ser sobrescrito. Em `LengthRule.check` isso protege o algoritmo: as subclasses só trocam as peças.
- Perguntas:
  - Por que as folhas são `final`? Porque ninguém mais precisa estender; fecha a hierarquia e deixa claro quem são os casos concretos.
  - Por que o método `check` da `LengthRule` é `final`? Para nenhuma subclasse mudar a ordem das checagens; o que varia está nos métodos abstratos.

### Interface (com default e static)

- Onde: `shared/domain/ValidationRule.java`: método abstrato `check`, método `default boolean accepts(T value)` e método `static firstProblem(...)`.
- Explicação: a interface é um contrato (`check`). O `default` entrega um método pronto que as implementações herdam sem escrever, igual ao `foo()` do `Brincavel` na aula 9; o `static` pertence à interface e é chamado por `ValidationRule.firstProblem(...)`, como o `algo()` do `Brincavel`. Como na aula, métodos concretos numa interface precisam ser `default` ou `static`.
- Perguntas:
  - Para que serve o `default` aqui? `accepts` responde sim ou não usando `check`; nenhuma regra precisa repetir isso.
  - Preciso sobrescrever um `default`? Não é obrigatório (o abstrato é); pode sobrescrever se quiser, como o `Cachorro` faz com `foo()`.
  - Por que `ValidationRule` é interface e `LengthRule` abstrata? Interface define o que toda regra faz; a abstrata reaproveita código de um subgrupo (as regras de tamanho).

### Várias interfaces (múltiplas)

- Onde: no código atual, cada classe implementa uma interface (`ValidationRule`). O exemplo de várias interfaces ao mesmo tempo não existe ainda em código nosso. Na hierarquia 1 isso pode aparecer se algum provider precisar de um segundo contrato; até lá, não cite como exemplo do projeto.
- Explicação: em Java uma classe estende uma única classe, mas implementa várias interfaces, como `Cachorro extends Animal implements Brincavel, Nadavel` na aula 9. Neste projeto o que existe de real é a classe que combina uma superclasse e uma interface: `SlugLengthRule extends LengthRule`, e `LengthRule implements ValidationRule`.
- Perguntas:
  - Por que Java não tem herança múltipla de classes? Para evitar ambiguidade (qual implementação herdar); interfaces são contratos e `default` em conflito exige que a classe resolva.
  - Dá para mostrar `extends` e `implements` juntos no projeto? Sim: `LengthRule<T, P> implements ValidationRule<T, P>` (e, pela cadeia, `SlugLengthRule` é uma `LengthRule` e é uma `ValidationRule`).

### Polimorfismo de subtipo

- Onde: `shared/domain/ValidationRule.java` (`firstProblem` percorre `List<? extends ValidationRule<T, P>>`); chamado de `profile/domain/Slug.java` (`check`) e `identity/domain/PasswordPolicy.java` (`check`). Para erros: `shared/api/DomainErrors.java` (um único `@ExceptionHandler` para `DomainException`, que chama `e.code()`, `e.kind()` e `e.retryAfter()`).
- Explicação: é o PetShop da aula 9. O `PetShop` guarda `ArrayList<Animal>` e chama `a.emitirSom()` sem saber se é cachorro ou gato; cada objeto responde do seu jeito. Aqui, `firstProblem` guarda regras diferentes (tamanho, caracteres, hífen, reservadas) e chama `rule.check(value)`; cada classe aplica a sua regra. No tratamento de erro, `TooManyAttemptsException` sobrescreve `retryAfter()` e o handler adiciona o cabeçalho `Retry-After` sem nenhum `instanceof`.
- Perguntas:
  - Onde está o polimorfismo? No loop de `ValidationRule.firstProblem`: a variável é do tipo da interface e o `check` executado depende da classe do objeto em tempo de execução.
  - O que muda se eu criar uma nova regra de slug? Crio a classe que implementa `ValidationRule<String, Problem>` e coloco na lista `RULES` de `Slug`; `firstProblem` não muda.
  - O que muda se eu criar um novo erro de domínio? Crio a subclasse com `super(codigo, tipo)`; o `DomainErrors` já trata, sem mudança.
  - Qual a diferença entre sobrecarga e polimorfismo? Sobrecarga é decidida na compilação pela lista de parâmetros; polimorfismo de subtipo é decidido na execução pela classe real do objeto.

---

## Hierarquias, uma a uma

### 4. ValidationRule (feito)

```
      interface ValidationRule<T, P>
             ^                     ^
  abstract LengthRule<T,P>         |
        ^          ^               +--- SlugCharactersRule, SlugHyphenRule, SlugReservedRule
        |          |               +--- CommonPasswordRule, PasswordEqualsEmailRule
final SlugLengthRule  final PasswordLengthRule
```

- Arquivos: `shared/domain/ValidationRule.java`, `shared/domain/LengthRule.java`; `profile/domain/Slug*Rule.java`; `identity/domain/PasswordLengthRule.java`, `CommonPasswordRule.java`, `PasswordEqualsEmailRule.java`, `PasswordCandidate.java`, `PasswordPolicy.java`. Testes: `shared/domain/ValidationRuleTest.java`, `profile/domain/SlugRulesTest.java`, `identity/domain/PasswordRulesTest.java`.
- Roteiro para explicar: (1) a interface diz "toda regra tem `check`"; (2) a `LengthRule` abstrata repete o que é igual nas regras de tamanho; (3) as folhas só dizem como medir e qual problema devolver; (4) `Slug.check` e `PasswordPolicy.check` têm uma lista e perguntam a cada regra na ordem, como o PetShop.
- Perguntas:
  - Por que `PasswordCandidate`? A regra "senha igual ao e-mail" precisa de dois dados; `ValidationRule<T, P>` recebe um único valor, então empacotamos senha e e-mail num tipo só.
  - Por que o tamanho da senha usa `codePointCount`? Emoji ocupa dois `char`; contamos caracteres de verdade (ADR 0012). Isso é o que `PasswordLengthRule.lengthOf` faz diferente de `SlugLengthRule.lengthOf`, e por isso o método é abstrato na base.
  - O que acontece com `null`? `LengthRule.check` trata `null` como tamanho zero, então devolve "curto demais" antes das outras regras verem o `null`.

### 5. DomainException (feito)

```
        abstract DomainException extends RuntimeException
             ^
   +---------+------------+--------------------+-------------------------+
   |                      |                    |                         |
final AccountRuleException  final InvalidSlugException  final SlugUnavailableException  final TooManyAttemptsException  final CollectionException
```

- Arquivos: `shared/domain/DomainException.java`, `shared/domain/ErrorKind.java`, as cinco subclasses (a quinta, `profile/domain/CollectionException.java`, cobre os erros da coleção: `ITEM_NOT_FOUND`, `CANNOT_REMOVE_SYNCED`, `CATALOG_UNAVAILABLE`, `NOTE_TOO_LONG`...), e `shared/api/DomainErrors.java`. Testes: `shared/domain/DomainExceptionTest.java`, `shared/api/DomainErrorsTest.java`, mais os testes de API existentes em `identity/` e `profile/` que conferem códigos e status.
- Roteiro para explicar: (1) todo erro de regra de negócio é uma `DomainException` com código e tipo; (2) o tipo (`ErrorKind`) é nosso, o mapeamento para HTTP fica na API; (3) um handler só atende todos; (4) o caso especial (`Retry-After`) é um método sobrescrito, não um `if`.
- Perguntas:
  - Por que `RuntimeException` como base? Erros de regra de negócio não precisam ser declarados em cada assinatura (`throws`); o handler da API os captura em um ponto.
  - Por que a `DomainException` não conhece HTTP? O domínio não pode depender da camada de API (ArchUnit confere); o `ErrorKind` é a ponte neutra.
  - Onde um novo erro precisa ser registrado? Em lugar nenhum além da própria classe.

### 1. ActivityProvider (planejado nas issues #28 a #34)

- Onde: ainda não existe código. Desenho no ADR 0018 e na issue #28 (interface), #30 (`LetterboxdProvider`), #34 (`GoodreadsProvider`), #29, #31 a #33 para os demais.
- Como vai ser explicado: igual ao `Animal` da aula 9. A interface `ActivityProvider` é o contrato; `RssActivityProvider` é uma classe abstrata com o que Letterboxd e Goodreads têm em comum (ler o feed RSS com timeout, converter o XML); `LetterboxdProvider` e `GoodreadsProvider` são `final` e só definem a URL e o mapeamento. `SyncService` percorre `List<ActivityProvider>` e chama o mesmo método em todos, como o `PetShop.gritaria()`.
- Perguntas prováveis (respostas quando implementado):
  - Por que `RssActivityProvider` é abstrata e não só interface? Porque tem código e campos compartilhados.
  - Por que AniList, Steam e Last.fm implementam a interface direto? Não compartilham nada com RSS (falam JSON).
  - O que muda se eu criar um novo provider? Uma classe nova; o `SyncService` não muda.

### 2. CatalogProvider (interface, abstrata e `TmdbCatalog` feitos; demais catálogos na issue #22)

```
          interface CatalogProvider
                     ^
          abstract HttpCatalogProvider
 (template method: cache, timeout, erro)
                     ^
             final TmdbCatalog
   (depois: AniList, Open Library, IGDB, Deezer)
```

- Arquivos: `media/domain/CatalogProvider.java` (interface), `media/domain/CatalogRegistry.java`, `media/domain/CatalogEntry.java`, `shared/domain/MediaCategory.java`; `media/infra/HttpCatalogProvider.java` (abstrata), `media/infra/TmdbCatalog.java` (final); `media/infra/CatalogConfig.java` só liga tudo no Spring, fora da hierarquia. Testes: `media/infra/HttpCatalogProviderTest.java` (a classe abstrata em ação, com uma subclasse mínima), `media/infra/TmdbCatalogTest.java`, `media/domain/CatalogDomainTest.java` (registro), `media/CatalogApiTests.java`.
- Roteiro para explicar: (1) a interface `CatalogProvider` diz o que todo catálogo faz: `id`, `serves(categoria)`, `isAvailable`, `search`, `fetch`; (2) `HttpCatalogProvider` é abstrata porque sabe o algoritmo mas não a plataforma; (3) `search` e `fetch` são `final` e formam o **template method**: checa disponibilidade, monta a URL (passo abstrato), chama com timeout de 5 s, usa cache de 10 min, converte qualquer falha em lista vazia mais um aviso no log, e entrega o corpo ao passo abstrato de interpretação; (4) `TmdbCatalog` (`final`) só preenche os passos: `searchUri`, `fetchUri`, `parseSearch`, `parseFetch`, e o gancho opcional `authorize` (coloca o token no cabeçalho); (5) `CatalogRegistry` recebe a lista, pergunta a cada um `serves(categoria)` e devolve o primeiro, como o loop do PetShop, sem `instanceof` nem `switch`.
- Como funciona sem chave: sem `TMDB_API_KEY`, `isAvailable()` devolve `false`; a busca devolve `{available:false, results:[]}` e nenhuma requisição é feita.
- Por que classe abstrata **e** interface? A interface é o contrato que o resto do código usa (`CatalogRegistry` e `CatalogSearch` só conhecem `CatalogProvider`); a classe abstrata guarda o código e o estado que todos os catálogos HTTP repetiriam (cliente, relógio, cache, tratamento de erro). Um catálogo que não fosse HTTP poderia implementar só a interface.
- Perguntas:
  - O que é o template method aqui? `search` fixa a ordem dos passos e deixa quatro deles abstratos; a subclasse não pode mudar a ordem (o método é `final`), só completar os passos.
  - Qual a diferença entre método abstrato e gancho (hook)? Abstrato é obrigatório (a subclasse não compila sem ele); `authorize` tem corpo vazio por padrão e só quem precisa de chave sobrescreve.
  - Por que `TmdbCatalog` é `final`? Nenhuma outra classe deve herdar dele; cada plataforma é uma folha.
  - Por que o erro vira lista vazia e não exceção? Um provedor fora do ar não pode derrubar a tela nem os outros provedores; o aviso no log traz só o nome do provedor e o tipo de falha, nunca a URL nem o token.
  - Como adiciono o AniList? Uma classe nova `AniListCatalog extends HttpCatalogProvider` e um `@Bean` em `CatalogConfig`; nada mais muda.

### 3. Plan (feito na issue #24)

```
        interface Plan
 (code, highlightLimit, canApply(ThemeTier),
  default hasRoomFor(int))
             ^
      +------+------+
      |             |
final FreePlan   final PackPlan
(4 destaques,    (12 destaques,
 temas grátis)    todos os temas)
```

- Arquivos: `profile/domain/Plan.java` (interface), `FreePlan.java`, `PackPlan.java`, `ThemeTier.java` (enum `FREE`/`PREMIUM`); quem decide qual plano o usuário tem: `profile/application/PlanResolver.java` (interface) e `EveryoneIsFree.java` (por enquanto devolve `FreePlan` para todos); quem usa: `HighlightList.highlight(item, plan)` em `profile/domain/HighlightList.java` e `profile/application/ProfileHighlights.java`. Testes: `profile/domain/PlanTest.java`, `profile/domain/HighlightListTest.java` (sem Spring), `profile/HighlightApiTests.java` (a mesma API com `FreePlan` e com `PackPlan`).
- Roteiro para explicar: (1) a interface `Plan` diz o que qualquer plano responde: limite de destaques (BR-02) e se um tema pode ser aplicado (BR-05); (2) `FreePlan` e `PackPlan` são `final` e só devolvem números e respostas diferentes; (3) a regra "no máximo N destaques" está em `HighlightList.highlight`, que recebe um `Plan` e pergunta `plan.hasRoomFor(tamanho)`; ela nunca pergunta "o usuário comprou o pacote?"; (4) o método `default hasRoomFor` está na interface porque a conta é a mesma para todo plano, só o limite muda.
- Comparação com a aula 9: `Plan` é o `Animal`/`Pet`, `FreePlan` e `PackPlan` são `Cachorro` e `Gato`, `HighlightList.highlight(item, plan)` é o `PetShop.gritaria(animal)`: o método recebe o tipo base e o comportamento muda conforme o objeto real. Trocar de plano é entregar outro objeto ao mesmo método.
- Por que interface e não classe abstrata? Os dois planos não compartilham campos nem construtor; só um contrato (e uma conta pequena, o `default`). Classe abstrata seria herança sem nada para herdar, e ainda gastaria a única herança que a classe pode ter. Com interface, um plano futuro pode estender qualquer coisa.
- Onde fica o único `if`? Na fábrica: o `PlanResolver` escolhe qual objeto entregar (hoje sempre `FreePlan`; na issue #39 devolverá `PackPlan` se o usuário tiver o entitlement do pacote). É a única decisão sobre quem tem o quê, e fica em um lugar só; ninguém depois dela testa o tipo do plano (sem `instanceof`).
- Perguntas:
  - Como trocar de plano sem `if`? O `ProfileHighlights` chama `plans.planOf(userId)`, recebe um `Plan` e passa adiante; o teste de API troca o `PlanResolver` por outro que devolve `PackPlan` e o limite vira 12 sem mexer em nenhuma linha de `HighlightList`.
  - E se criarem um plano "Premium" com 30 destaques? Uma classe nova `PremiumPlan implements Plan` e o resolvedor passa a entregá-la; as regras de destaque e de tema não mudam.
  - Por que `Plan` não tem anotação do Spring ou do JPA? É uma hierarquia nossa (ADR 0018): Java puro, testada sem contexto.
  - Por que `canApply` recebe `ThemeTier` e não um tema? Os temas só existem na issue #37; `ThemeTier` (grátis ou premium) já deixa o método com sentido hoje. Quando existirem, o método pode receber o tema.
  - Por que o erro de limite é `CONFLICT` (HTTP 409) e não `INVALID` (400)? O pedido é válido; ele conflita com o estado atual (a lista está cheia para o seu plano). Depois de remover um destaque, o mesmo pedido funciona.

## Destaques (issue #24)

Regras de negócio em classes comuns: `profile/domain/HighlightList.java` (entidade com as regras: `highlight`, `unhighlight`, `dropItem`, `reorder`), `Highlight.java`, `HighlightException.java` (sexta subclasse de `DomainException`, com códigos como `HIGHLIGHT_LIMIT_REACHED`, `ALREADY_HIGHLIGHTED`, `INVALID_ORDER`); serviço em `profile/application/ProfileHighlights.java`.

Leitura pública (issues #16 e #26): `profile/application/PublicProfiles.java` monta o modelo de leitura (`PublicProfile`, records imutáveis compostos: o perfil tem uma lista de `Section`, que tem uma lista de `PublicItem`) e `PublicProfileException.java` (sétima subclasse de `DomainException`, `PROFILE_NOT_FOUND` e `ITEM_NOT_FOUND`, sempre `NOT_FOUND`) é tratada pelo mesmo `DomainErrors`, sem `if` por tipo. `media/domain/MediaLinks.java` monta o link do catálogo a partir de uma lista fixa de hospedeiros.

- BR-02 (limite pelo plano), BR-07 (uma obra uma vez por lista), FR-11 ("Favoritos de tudo" é a lista com categoria `null`, aceita qualquer categoria e usa o mesmo limite).
- Esconder ou apagar um item da coleção tira o item dos destaques (`ProfileCollection.leaveHighlights`).
- Reordenar: o cliente manda todos os ids da lista na nova ordem; se não for exatamente o conjunto atual, `INVALID_ORDER`.

---

## Perguntas gerais que o professor pode fazer

- **Explique a hierarquia que você mais gosta.** Use a 4: interface genérica, classe abstrata `LengthRule`, folhas `final`, e a lista percorrida em `firstProblem`.
- **O que é polimorfismo?** O mesmo código (`rule.check(value)`) executa comportamentos diferentes conforme a classe real do objeto, decidido em tempo de execução.
- **Qual a diferença entre classe abstrata e interface?** Abstrata pode ter campos, construtor e métodos prontos, e a subclasse só herda de uma; interface é contrato (com `default` e `static` opcionais) e uma classe pode implementar várias.
- **Por que o Spring não conta?** Porque quem escreveu a hierarquia foi o framework, não nós; o professor pediu código próprio.
- **O que é `Optional`/`Maybe`?** Um tipo genérico que representa "tem valor ou não tem", em vez de `null`.
- **Mostre um construtor com `super`.** `AccountRuleException(Rule rule)` chama `super(rule.name(), ...)`.
- **Mostre um método abstrato implementado.** `PasswordLengthRule.lengthOf` implementa o abstrato de `LengthRule`.
