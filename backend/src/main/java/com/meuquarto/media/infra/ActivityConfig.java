package com.meuquarto.media.infra;

import com.meuquarto.media.domain.ActivityProvider;
import com.meuquarto.media.domain.ActivityRegistry;
import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring wiring for the activity providers, kept outside the hierarchy on purpose: the provider classes
 * know nothing about Spring. Every {@link ActivityProvider} bean goes into the registry; a new platform
 * is just a new bean. A platform without a provider yet (Steam, #33)
 * answers {@code PROVIDER_UNAVAILABLE} when linked.
 */
@Configuration
class ActivityConfig {

    /** Letterboxd reads a public RSS feed: no key, fixed origin. */
    @Bean
    ActivityProvider letterboxdProvider() {
        return new LetterboxdProvider();
    }

    /** Goodreads reads public per-shelf RSS feeds: no key, fixed origin. */
    @Bean
    ActivityProvider goodreadsProvider() {
        return new GoodreadsProvider();
    }

    /**
     * Last.fm needs an API key, read only from the environment ({@code LASTFM_API_KEY}). Without it the
     * provider reports itself unavailable and nothing is sent.
     */
    @Bean
    ActivityProvider lastFmProvider(@Value("${meuquarto.activity.lastfm.api-key:}") String apiKey) {
        return new LastFmProvider(apiKey, Clock.systemUTC());
    }

    @Bean
    ActivityRegistry activityRegistry(ObjectProvider<ActivityProvider> providers) {
        return new ActivityRegistry(providers.orderedStream().toList());
    }
}
