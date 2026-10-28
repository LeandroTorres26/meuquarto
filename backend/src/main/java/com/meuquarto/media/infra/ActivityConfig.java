package com.meuquarto.media.infra;

import com.meuquarto.media.domain.ActivityProvider;
import com.meuquarto.media.domain.ActivityRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring wiring for the activity providers, kept outside the hierarchy on purpose: the provider classes
 * know nothing about Spring. Every {@link ActivityProvider} bean goes into the registry; a new platform
 * is just a new bean. Until the first concrete provider exists (#30 to #34) the registry is empty and
 * linking any platform answers {@code PROVIDER_UNAVAILABLE}.
 */
@Configuration
class ActivityConfig {

    @Bean
    ActivityRegistry activityRegistry(ObjectProvider<ActivityProvider> providers) {
        return new ActivityRegistry(providers.orderedStream().toList());
    }
}
