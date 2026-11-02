package com.meuquarto.media.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.meuquarto.shared.domain.ValidationRule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class ProfileUrlsTest {

    @Test
    void everyPlatformHasAFixedHostAndPathTemplate() {
        assertThat(ProfileUrls.urlFor(ProviderId.LETTERBOXD, "leandro")).contains("https://letterboxd.com/leandro/");
        assertThat(ProfileUrls.urlFor(ProviderId.ANILIST, "leandro")).contains("https://anilist.co/user/leandro/");
        assertThat(ProfileUrls.urlFor(ProviderId.MYANIMELIST, "Leandro-T_1")).contains("https://myanimelist.net/profile/Leandro-T_1");
        assertThat(ProfileUrls.urlFor(ProviderId.LASTFM, "Leandro_T")).contains("https://www.last.fm/user/Leandro_T");
        assertThat(ProfileUrls.urlFor(ProviderId.GOODREADS, "12345678")).contains("https://www.goodreads.com/user/show/12345678");
        assertThat(ProfileUrls.urlFor(ProviderId.STEAM, "leandro")).contains("https://steamcommunity.com/id/leandro");
        assertThat(ProfileUrls.urlFor(ProviderId.BACKLOGGD, "leandro")).contains("https://backloggd.com/u/leandro/");
        assertThat(ProfileUrls.urlFor(ProviderId.TRAKT, "leandro")).contains("https://trakt.tv/users/leandro");
        assertThat(ProfileUrls.urlFor(ProviderId.STORYGRAPH, "leandro")).contains("https://app.thestorygraph.com/profile/leandro");
    }

    @Test
    void dotsHyphensAndUnderscoresInsideANameAreKept() {
        assertThat(ProfileUrls.urlFor(ProviderId.TRAKT, "ana.maria-01_x")).contains("https://trakt.tv/users/ana.maria-01_x");
    }

    @ParameterizedTest
    @EnumSource(ProviderId.class)
    void aHostileNameNeverBecomesPartOfAnUrl(ProviderId platform) {
        for (String hostile : new String[] {"../admin", "..", ".", "a/b", "a?x=y", "a#frag", "x@evil.example", "a b",
                "a\r\nLocation: https://evil.example", "a\nb", "café", "ＬＥＡＮ", "a%2fb",
                "https://evil.example/x", "a\\b", "", " ", "a".repeat(65)}) {
            assertThat(ProfileUrls.urlFor(platform, hostile)).as("%s %s", platform, hostile).isEmpty();
        }
        assertThat(ProfileUrls.urlFor(platform, null)).isEmpty();
    }

    @Test
    void theNullPlatformHasNoLink() {
        assertThat(ProfileUrls.urlFor(null, "leandro")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"leandro", "Leandro_01", "ana.maria", "a-b", "a", "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"})
    void linkOnlyUsernamesAcceptConservativeNames(String username) {
        assertThat(ValidationRule.firstProblem(ProfileUrls.linkOnlyUsernameRules(), username)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx", "../admin", "..", ".", "a/b", "a?b", "a#b", "a@b", "a b", "a\r\nb",
            "café", "ＬＥ", "http://evil.example"})
    void linkOnlyUsernamesRefuseAnythingElse(String username) {
        assertThat(ValidationRule.firstProblem(ProfileUrls.linkOnlyUsernameRules(), username)).isPresent();
    }

    @Test
    void onlyBackloggdTraktAndStorygraphAreLinkOnly() {
        assertThat(ProviderId.linkOnlyPlatforms())
                .containsExactly(ProviderId.BACKLOGGD, ProviderId.TRAKT, ProviderId.STORYGRAPH);
        assertThat(ProviderId.LETTERBOXD.linkOnly()).isFalse();
        assertThat(ProviderId.STEAM.linkOnly()).isFalse();
    }

    @Test
    void theDeclarationOrderIsTheFixedPlatformOrderOfTheProfile() {
        assertThat(ProviderId.values()).containsExactly(ProviderId.LETTERBOXD, ProviderId.ANILIST, ProviderId.MYANIMELIST,
                ProviderId.LASTFM, ProviderId.STEAM, ProviderId.GOODREADS, ProviderId.BACKLOGGD, ProviderId.TRAKT, ProviderId.STORYGRAPH);
    }
}
