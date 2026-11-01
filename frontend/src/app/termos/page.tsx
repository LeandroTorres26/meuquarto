import type { Metadata } from "next";
import Link from "next/link";
import { LegalLayout, LegalSection } from "@/components/LegalLayout";
import { LEGAL_CONTACT_EMAIL } from "@/lib/legal";

export const metadata: Metadata = {
  title: "Termos de uso do meuquarto",
  description:
    "As regras para usar o meuquarto: sua conta, as contas que você conecta, o conteúdo do perfil e como excluir tudo.",
  alternates: { canonical: "/termos" },
};

// S15 (docs/design/telas/termos.html). Static text that describes what the system does today:
// revise it whenever the behavior it describes changes.
export default function TermsPage() {
  return (
    <LegalLayout current="termos" title="Termos de uso">
      <LegalSection title="O que é o meuquarto">
        <p className="t-body">
          Um perfil público que mostra filmes, séries, animes, mangás, livros, games e música de que
          você gosta. Parte dos dados vem das plataformas que você conecta; o resto você fixa à mão.
          Ao criar a conta você aceita estes termos e a{" "}
          <Link href="/privacidade">política de privacidade</Link>.
        </p>
      </LegalSection>
      <LegalSection title="Sua conta">
        <p className="t-body">
          Você precisa de um e-mail válido, de uma senha e de um endereço próprio para o perfil
          (meuquarto.com.br/seu-endereco). Você é responsável por manter a senha em segredo e pelo
          que acontece na sua conta. Endereços que imitem outras pessoas ou marcas podem ser
          removidos.
        </p>
      </LegalSection>
      <LegalSection title="Perfil público">
        <p className="t-body">
          O perfil, os destaques e as notas que você publica ficam visíveis para qualquer pessoa que
          tenha o link, inclusive buscadores e redes sociais. Não escreva nas notas nada que você
          não queira tornar público. Conteúdo ilegal, ofensivo ou que viole direitos de terceiros
          pode ser removido.
        </p>
      </LegalSection>
      <LegalSection title="Contas vinculadas">
        <p className="t-body">
          Lemos apenas dados públicos dos perfis que você informa, como o diário do Letterboxd ou a
          lista do AniList. Não pedimos sua senha dessas plataformas nem guardamos acesso a elas.
          Você pode desvincular quando quiser; os dados sincronizados são apagados e os destaques
          manuais ficam.
        </p>
      </LegalSection>
      <LegalSection title="Imagens e conteúdo">
        <p className="t-body">
          Capas e metadados vêm de bases como TMDB, AniList, Open Library, IGDB e Deezer, com seus
          créditos. Este produto usa a API do TMDB, mas não é endossado nem certificado pelo TMDB.
          As notas que você escreve são de sua responsabilidade.
        </p>
      </LegalSection>
      <LegalSection title="Compras">
        <p className="t-body">
          Ainda não há nada à venda. Quando a loja abrir, packs e temas serão compras únicas e o
          preço será mostrado antes do pagamento. Estes termos serão atualizados com as regras da
          compra antes disso.
        </p>
      </LegalSection>
      <LegalSection title="Disponibilidade">
        <p className="t-body">
          O meuquarto é um projeto acadêmico em desenvolvimento e é oferecido como está, sem
          garantia de funcionamento contínuo. Recursos podem mudar ou sair do ar, e os dados vindos
          de outras plataformas dependem de elas estarem disponíveis.
        </p>
      </LegalSection>
      <LegalSection title="Excluir a conta">
        <p className="t-body">
          Você pode excluir a conta a qualquer momento em Conta, no painel. A exclusão é imediata:
          apagamos perfil, destaques, notas e contas vinculadas, e o endereço do perfil deixa de
          existir. Veja os detalhes na <Link href="/privacidade">política de privacidade</Link>.
        </p>
      </LegalSection>
      <p className="t-sm">
        Dúvidas: <a href={`mailto:${LEGAL_CONTACT_EMAIL}`}>{LEGAL_CONTACT_EMAIL}</a>
      </p>
    </LegalLayout>
  );
}
