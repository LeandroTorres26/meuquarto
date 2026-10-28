package com.meuquarto.media.infra;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring wiring for AniList, kept outside the hierarchy on purpose. There is exactly one bean and its
 * declared type is the concrete class, so the container offers that same instance wherever a
 * {@code CatalogProvider} (catalog registry) or an {@code ActivityProvider} (activity registry) is asked
 * for. It is not two objects: it is one object seen through two interfaces.
 */
@Configuration
class AniListConfig {

    @Bean
    AniListProvider aniListProvider() {
        return new AniListProvider(Clock.systemUTC());
    }
}
