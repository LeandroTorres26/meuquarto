package com.meuquarto.profile.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meuquarto.profile.domain.Slug.Problem;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** BR-01: slug format, length and reserved words. */
class SlugTest {

    @Nested
    class Accepted {

        @ParameterizedTest
        @ValueSource(strings = {"leandro", "abc", "a1b", "leandro-torres", "a-b-c", "123", "nome-com-30-caracteres-exatos0"})
        void validSlugs(String value) {
            assertThat(Slug.of(value).value()).isEqualTo(value);
        }

        @Test
        void exactlyThirtyCharacters() {
            String thirty = "a".repeat(30);
            assertThat(Slug.of(thirty).value()).hasSize(30);
        }

        @ParameterizedTest
        @CsvSource({"'  Leandro  ',leandro", "LEANDRO-Torres,leandro-torres"})
        void inputIsTrimmedAndLowercased(String raw, String expected) {
            assertThat(Slug.of(raw).value()).isEqualTo(expected);
        }
    }

    @Nested
    class Rejected {

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"a", "ab", "  ab  "})
        void tooShort(String raw) {
            assertProblem(raw, Problem.TOO_SHORT);
        }

        @Test
        void tooLong() {
            assertProblem("a".repeat(31), Problem.TOO_LONG);
        }

        @ParameterizedTest
        @ValueSource(strings = {"joão", "leandro torres", "leandro_torres", "leandro.torres", "le@ndro", "café", "x/y/z", "<script>"})
        void invalidCharacters(String raw) {
            assertProblem(raw, Problem.INVALID_CHARACTERS);
        }

        @ParameterizedTest
        @ValueSource(strings = {"-leandro", "leandro-", "lean--dro", "---"})
        void hyphenOnlyBetweenLettersOrDigits(String raw) {
            assertProblem(raw, Problem.INVALID_HYPHEN);
        }

        @ParameterizedTest
        @ValueSource(strings = {"painel", "entrar", "cadastro", "api", "termos", "privacidade", "loja", "admin", "meuquarto", "Painel"})
        void reservedWords(String raw) {
            assertProblem(raw, Problem.RESERVED);
        }

        @Test
        void invalidValueCannotBeConstructedDirectly() {
            assertThatThrownBy(() -> new Slug("Leandro"))
                    .isInstanceOf(InvalidSlugException.class)
                    .extracting(e -> ((InvalidSlugException) e).problem())
                    .isEqualTo(Problem.INVALID_CHARACTERS);
        }

        private void assertProblem(String raw, Problem expected) {
            assertThat(Slug.check(Slug.normalize(raw))).contains(expected);
            assertThatThrownBy(() -> Slug.of(raw))
                    .isInstanceOf(InvalidSlugException.class)
                    .extracting(e -> ((InvalidSlugException) e).problem())
                    .isEqualTo(expected);
        }
    }
}
