package com.meuquarto.media.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.meuquarto.media.FakeActivityProvider;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** The contract, the username rules and the registry, with no Spring and no network. */
class ActivityProviderTest {

    private final ActivityProvider provider = new FakeActivityProvider();

    @Test
    void normalizingTrimsAndTheFakeLowerCases() {
        assertThat(provider.normalizeUsername("  Leandro_ ")).isEqualTo("leandro_");
        assertThat(provider.normalizeUsername(null)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ab", "leandro_01", "a23456789012345"})
    void acceptsGoodUsernames(String name) {
        assertThat(provider.validateUsernameFormat(name)).isEmpty();
    }

    @Test
    void lengthIsCheckedBeforeCharacters() {
        assertThat(provider.validateUsernameFormat("")).contains(UsernameProblem.TOO_SHORT);
        assertThat(provider.validateUsernameFormat("a")).contains(UsernameProblem.TOO_SHORT);
        assertThat(provider.validateUsernameFormat("a2345678901234567")).contains(UsernameProblem.TOO_LONG);
        assertThat(provider.validateUsernameFormat("../../../../../../x?y#z")).contains(UsernameProblem.TOO_LONG);
    }

    @ParameterizedTest
    @ValueSource(strings = {"../etc", "a?b=c", "a#frag", "x@evil.com", "a/b", "a b", "a\b", "a%2fb", "ab\n", "a.b",
            "ＬＥＡＮ", "café", "ab‮", "аб", "a:80", "a;b"})
    void refusesCharactersThatCouldChangeAUrl(String name) {
        Optional<UsernameProblem> problem = provider.validateUsernameFormat(name);
        assertThat(problem).isPresent();
    }

    @Test
    void theRegistryFindsByIdWithoutAskingTheClass() {
        ActivityProvider fake = new FakeActivityProvider();
        ActivityRegistry registry = new ActivityRegistry(List.of(fake));

        assertThat(registry.find(ProviderId.LETTERBOXD)).containsSame(fake);
        assertThat(registry.find(ProviderId.STEAM)).isEmpty();
        assertThat(registry.all()).containsExactly(fake);
    }

    @Test
    void anEmptyRegistryFindsNothing() {
        assertThat(new ActivityRegistry(List.of()).find(ProviderId.LETTERBOXD)).isEmpty();
    }

    @Test
    void anActivityEntryNeedsAllItsParts() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new ActivityEntry(null, ActivityType.FINISHED, java.time.Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
