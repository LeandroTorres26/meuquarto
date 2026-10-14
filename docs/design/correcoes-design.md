# Prompt de correções para o Claude Design

Cole o texto abaixo no Claude Design, no canvas "meuquarto telas". Revisão feita em 2026-10-12 sobre a cópia em `docs/design/telas` e `docs/design-system`.

---

Revisei as telas e o design system contra o planejamento do projeto. A estrutura está boa: as 16 telas existem em desktop, tablet e celular, os limites (4 e 12), o cooldown de sincronização, Stripe com Pix ou cartão, a atribuição das bases e a exclusão de conta estão coerentes. Faça só as correções abaixo e não mude o que não foi citado.

## 1. Remover a importação por CSV da tela S9 (PainelContas)

O card "Outra plataforma" (Skoob, StoryGraph e Backloggd, botão "Importar CSV") promete uma funcionalidade que está no roadmap, não no MVP da P2. Troque o botão por um estado desabilitado com o rótulo "Em breve" e o texto "Importação por arquivo CSV chega em uma próxima versão." Mantenha a frase sobre séries serem fixadas à mão em Destaques. Aplique em desktop, tablet e celular.

## 2. Preços da loja

`Loja.dc.html` usa `R$ [PREÇO]` em dois lugares. Use estes valores provisórios e deixe-os como props editáveis (`preco-pack`, `preco-tema`):

- Pack de destaques (4 para 12): R$ 14,90
- Tema premium (Locadora): R$ 9,90

Quanto ao tema Estufa, ele aparece como já comprado em `Compras`; mantenha o preço coerente com o de Locadora.

## 3. Estado de limite e upsell (S7)

A tela mostra o limite atingido, mas "Favoritos de tudo" também tem limite de 4 no plano grátis. Garanta que a aba "Favoritos de tudo 4/4" mostre o mesmo upsell com a mensagem "O plano grátis guarda 4 destaques em Favoritos de tudo. Com um pack, são 12." e que o botão de adicionar fique desabilitado nela.

## 4. Estados que faltam

Adicione, sem criar novas telas, estes estados como variações por prop:

- **S9:** sincronização em andamento (spinner e botão desabilitado com "Sincronizando…").
- **S8:** busca sem resultados ("Nada encontrado para “…”. Tente outro título.") e erro de rede ("Não deu para buscar agora. Tente de novo.").
- **S10:** categoria sem sincronização (Séries), onde "Automático" fica desabilitado com a explicação "Séries não têm plataforma para sincronizar. Escolha à mão."

## 5. Componentes do design system ainda sem redesenho

O README do design system diz que Button, NowCard, Toast e LimitUpsell ainda herdam só os tokens e aguardam redesenho. Redesenhe os quatro seguindo a regra do README (controles funcionais sóbrios: sem inclinação, fita ou escrita à mão), mostre todos os estados (padrão, hover, foco, desabilitado, carregando para Button; vazio e carregando para NowCard) e atualize `bundle.js`, `bundle.css`, `index.d.ts` e os `preview.html` de cada um. Isso é necessário porque vou implementar os componentes em React a partir deles.

## 6. Referências e documentação

- Os arquivos `.dc.html` apontam para `ds/meuquarto/...`, mas no repositório o design system fica em `docs/design-system/`. Não mude os arquivos do canvas; em vez disso, acrescente ao `README.md` de `docs/design/telas` uma seção "Caminhos" com a tabela de equivalência (`ds/meuquarto/` = `docs/design-system/`, `/_blob/<id>` = `covers/cNN.svg`, `./support.js` = não existe e não é necessário) para o agente de desenvolvimento não tropeçar.
- Liste no README, por tela, as props `data-props` que mudam o estado (por exemplo `estado` em S12, variações novas de S8, S9 e S10), para que os estados possam ser reproduzidos.

## 7. Tema

Confirme e documente qual tema é gratuito e qual é pago: `noite` (padrão) e `dia` devem ser os dois gratuitos, e `locadora` e `estufa` premium. Registre isso na tabela de temas de `docs/design-system/README.md`, porque a regra BR-05 (tema premium só para quem comprou) depende dela.

## 8. Quarto (S5)

O README diz para refazer o quarto em Three.js e assets Kenney. A decisão de tecnologia ainda não foi tomada (será um ADR depois). Troque a frase por: "Protótipo em CSS 3D. Tecnologia final a decidir; manter o mesmo conteúdo, a mesma câmera e os mesmos controles." Não altere a tela.

Ao terminar, liste os arquivos que mudaram.
