package com.meuquarto.profile.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meuquarto.profile.domain.ProfileException.Problem;
import com.meuquarto.shared.domain.LengthRule;
import com.meuquarto.shared.domain.ValidationRule;
import org.junit.jupiter.api.Test;

/** FR-05: the display name and bio rules, each rule alone and then the value objects that use them. */
class ProfileTextRulesTest {

    @Test
    void displayNameLengthRuleIsALengthRuleCountingCodePoints() {
        LengthRule<String, Problem> rule = new DisplayNameLengthRule();
        assertThat(rule.check("")).contains(Problem.DISPLAY_NAME_REQUIRED);
        assertThat(rule.check(null)).contains(Problem.DISPLAY_NAME_REQUIRED);
        assertThat(rule.check("a")).isEmpty();
        assertThat(rule.check("a".repeat(40))).isEmpty();
        assertThat(rule.check("a".repeat(41))).contains(Problem.DISPLAY_NAME_TOO_LONG);
        // 40 emoji are 80 chars but 40 code points: they fit.
        assertThat(rule.check("🎬".repeat(40))).isEmpty();
    }

    @Test
    void bioLengthRuleAllowsEmptyAndStopsAt160() {
        LengthRule<String, Problem> rule = new BioLengthRule();
        assertThat(rule.check("")).isEmpty();
        assertThat(rule.check(null)).isEmpty();
        assertThat(rule.check("a".repeat(160))).isEmpty();
        assertThat(rule.check("a".repeat(161))).contains(Problem.BIO_TOO_LONG);
    }

    @Test
    void plainTextRuleRefusesControlCharactersAndLineBreaksOnlyWhereNotAllowed() {
        PlainTextRule name = new PlainTextRule(false, Problem.DISPLAY_NAME_INVALID);
        assertThat(name.check("Leandro Torres")).isEmpty();
        assertThat(name.check("line\nbreak")).contains(Problem.DISPLAY_NAME_INVALID);
        assertThat(name.check("tab\there")).contains(Problem.DISPLAY_NAME_INVALID);
        assertThat(name.check("nul\0")).contains(Problem.DISPLAY_NAME_INVALID);

        ValidationRule<String, Problem> bio = new PlainTextRule(true, Problem.BIO_INVALID);
        assertThat(bio.accepts("two\nlines")).isTrue();
        assertThat(bio.check("bell\u0007")).contains(Problem.BIO_INVALID);
    }

    @Test
    void displayNameIsTrimmedAndValidated() {
        assertThat(DisplayName.of("  Leandro  ").value()).isEqualTo("Leandro");
        assertThatThrownBy(() -> DisplayName.of("   ")).isInstanceOfSatisfying(ProfileException.class,
                e -> assertThat(e.problem()).isEqualTo(Problem.DISPLAY_NAME_REQUIRED));
        assertThatThrownBy(() -> DisplayName.of("a".repeat(41))).isInstanceOfSatisfying(ProfileException.class,
                e -> assertThat(e.code()).isEqualTo("DISPLAY_NAME_TOO_LONG"));
        assertThatThrownBy(() -> DisplayName.of("a\nb")).isInstanceOfSatisfying(ProfileException.class,
                e -> assertThat(e.problem()).isEqualTo(Problem.DISPLAY_NAME_INVALID));
    }

    @Test
    void markupIsJustTextBecauseTheUiNeverRendersHtml() {
        // The rule is "plain text", not "escaped": a name with angle brackets is stored as typed and shown as text.
        assertThat(DisplayName.of("<b>x</b>").value()).isEqualTo("<b>x</b>");
    }

    @Test
    void bioIsTrimmedKeepsLineBreaksAndMayBeEmpty() {
        assertThat(Bio.of("  oi\r\ntudo bem  ").value()).isEqualTo("oi\ntudo bem");
        assertThat(Bio.of(null).isEmpty()).isTrue();
        assertThat(Bio.of("   ").isEmpty()).isTrue();
        assertThatThrownBy(() -> Bio.of("a".repeat(161))).isInstanceOfSatisfying(ProfileException.class,
                e -> assertThat(e.problem()).isEqualTo(Problem.BIO_TOO_LONG));
        assertThatThrownBy(() -> Bio.of("x\u0000y")).isInstanceOfSatisfying(ProfileException.class,
                e -> assertThat(e.problem()).isEqualTo(Problem.BIO_INVALID));
    }

    @Test
    void profileKeepsAnEmptyBioAsNone() {
        Profile profile = new Profile(1L, Slug.of("leandro"));
        assertThat(profile.displayName()).isEmpty();
        profile.rename(DisplayName.of("Leandro"));
        profile.changeBio(Bio.of("oi"));
        assertThat(profile.bio()).contains("oi");
        profile.changeBio(Bio.of(""));
        assertThat(profile.bio()).isEmpty();
    }
}
