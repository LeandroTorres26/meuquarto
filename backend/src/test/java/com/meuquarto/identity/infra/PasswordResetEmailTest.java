package com.meuquarto.identity.infra;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** The reset e-mail: pt-BR, the link in both parts, nothing loaded from outside. */
class PasswordResetEmailTest {

    private static final String LINK = "https://meuquarto.com.br/redefinir-senha?token=abc_DEF-123";

    @Test
    void textCarriesTheLinkAndTheRules() {
        String text = new PasswordResetEmail(LINK).text();

        assertThat(text).contains(LINK).contains("30 minutos").contains("uma vez").contains("Se não foi você");
    }

    @Test
    void htmlCarriesTheLinkAndLoadsNothingRemote() {
        String html = new PasswordResetEmail(LINK).html();

        assertThat(html).contains("href=\"" + LINK + "\"").contains("lang=\"pt-BR\"");
        assertThat(html).doesNotContain("<img").doesNotContain("<script").doesNotContain("src=");
    }

    @Test
    void htmlEscapesTheLink() {
        String html = new PasswordResetEmail("https://x.test/a?b=1&c=\"2\"<3").html();

        assertThat(html).contains("b=1&amp;c=&quot;2&quot;&lt;3").doesNotContain("\"2\"");
    }
}
