import type { Metadata } from "next";
import Link from "next/link";
import { LegalLayout, LegalSection } from "@/components/LegalLayout";
import { LEGAL_CONTACT_EMAIL } from "@/lib/legal";

export const metadata: Metadata = {
  title: "Privacidade no meuquarto",
  description:
    "Quais dados o meuquarto guarda, para quê, por quanto tempo, quais cookies usamos e como exercer seus direitos pela LGPD.",
  alternates: { canonical: "/privacidade" },
};

// S15 (docs/design/telas/termos.html, privacy tab). Written from what the system stores today
// (docs/data-model.md, docs/security.md). Keep it true: revise it whenever data or cookies change.
export default function PrivacyPage() {
  return (
    <LegalLayout current="privacidade" title="Privacidade">
      <LegalSection title="O que guardamos">
        <p className="t-body">Guardamos só o necessário para o meuquarto funcionar:</p>
        <ul className="t-body">
          <li>seu e-mail e uma versão embaralhada da senha (Argon2id); nunca a senha em si;</li>
          <li>o endereço do seu perfil, os destaques que você fixa e as notas que escreve;</li>
          <li>
            os nomes de usuário públicos que você conecta (por exemplo, Letterboxd ou AniList) e o
            que lemos deles: filmes, animes e mangás registrados, com título, capa e data;
          </li>
          <li>dados técnicos temporários para segurança, como o endereço IP em limites de uso.</li>
        </ul>
        <p className="t-body">
          Não guardamos senhas nem chaves de acesso das plataformas que você conecta.
        </p>
      </LegalSection>
      <LegalSection title="Para que usamos">
        <p className="t-body">
          Para manter você conectado, montar e mostrar seu perfil, sincronizar o que você conectou e
          proteger o serviço contra abuso. Não vendemos seus dados, não mostramos anúncios e não
          usamos ferramentas de análise ou rastreamento.
        </p>
      </LegalSection>
      <LegalSection title="O que é público">
        <p className="t-body">
          O perfil em meuquarto.com.br/seu-endereco, com destaques, notas e atividade recente, pode
          ser visto por qualquer pessoa e indexado por buscadores. Seu e-mail e sua senha nunca
          aparecem nele.
        </p>
      </LegalSection>
      <LegalSection title="Cookies">
        <p className="t-body">
          Usamos só cookies essenciais, sem rastreamento nem publicidade. Quando você entra, o
          cookie SESSION mantém a sessão (dura até 30 dias e não é acessível por scripts) e o
          XSRF-TOKEN protege os formulários contra requisições forjadas. Guardamos também no seu
          navegador, em armazenamento local, apenas a lembrança de que você viu o aviso de cookies.
        </p>
      </LegalSection>
      <LegalSection title="Com quem os dados passam">
        <p className="t-body">
          Para montar a coleção consultamos bases públicas de catálogo: TMDB, AniList, Letterboxd,
          Open Library, IGDB e Deezer. Elas recebem apenas o que buscamos (um título ou o nome de
          usuário público que você conecta), nunca seu e-mail. Quando a loja abrir, os pagamentos
          passarão pelo Stripe, que tratará os dados do cartão ou do Pix; nós não os guardamos.
        </p>
      </LegalSection>
      <LegalSection title="Por quanto tempo">
        <p className="t-body">
          Enquanto a conta existir. Ao excluí-la, apagamos tudo o que está ligado a ela, de uma vez:
          conta, perfil, destaques, notas, contas vinculadas e dados sincronizados. As sessões
          abertas expiram sozinhas. Se algum dia houver pagamentos, os registros deles serão
          mantidos sem dados pessoais, como exige a lei.
        </p>
      </LegalSection>
      <LegalSection title="Seus direitos">
        <p className="t-body">
          Pela LGPD, você pode pedir acesso, correção e exclusão dos seus dados, saber com quem eles
          são compartilhados e revogar o consentimento. A exclusão você faz sozinho em Conta, no
          painel; para os outros pedidos, escreva para{" "}
          <a href={`mailto:${LEGAL_CONTACT_EMAIL}`}>{LEGAL_CONTACT_EMAIL}</a>. Para entender as
          regras de uso, leia os <Link href="/termos">termos de uso</Link>.
        </p>
      </LegalSection>
    </LegalLayout>
  );
}
