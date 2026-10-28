package com.meuquarto.media.infra;

import com.meuquarto.media.domain.ActivityProvider;
import com.meuquarto.media.domain.ActivityRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring wiring for the activity providers, kept outside the hierarchy on purpose: the provider classes
 * know nothing about Spring. Every {@link ActivityProvider} bean goes into the registry; a new platform
 * is just a new bean. A platform without a provider yet (#32 to #34)
 * answers {@code PROVIDER_UNAVAILABLE} when linked.
 */
@Configuration
class ActivityConfig {

    /** Letterboxd reads a public RSS feed: no key, fixed origin. */
    @Bean
    ActivityProvider letterboxdProvider() {
        return new LetterboxdProvider();
    }

    @Bean
    ActivityRegistry activityRegistry(ObjectProvider<ActivityProvider> providers) {
        return new ActivityRegistry(providers.orderedStream().toList());
    }
}
