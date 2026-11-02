package com.meuquarto.media.infra;

import com.meuquarto.media.domain.CatalogProvider;
import com.meuquarto.media.domain.CatalogRegistry;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring wiring for the catalogs, kept outside the hierarchy on purpose: the provider classes know
 * nothing about Spring. The TMDB token and the IGDB (Twitch) credentials come only from the environment
 * ({@code TMDB_API_KEY}, {@code TWITCH_CLIENT_ID}, {@code TWITCH_CLIENT_SECRET}).
 */
@Configuration
class CatalogConfig {

    @Bean
    CatalogProvider tmdbCatalog(
            @Value("${meuquarto.catalog.tmdb.token:}") String token,
            @Value("${meuquarto.catalog.tmdb.base-url:https://api.themoviedb.org/3}") String baseUrl) {
        return new TmdbCatalog(HttpCatalogProvider.newClient(), Clock.systemUTC(), Duration.ofMinutes(10), baseUrl, token);
    }

    /** IGDB: the Twitch app id and secret come only from the environment; without them it is unavailable. */
    @Bean
    CatalogProvider igdbCatalog(
            @Value("${meuquarto.catalog.igdb.client-id:}") String clientId,
            @Value("${meuquarto.catalog.igdb.client-secret:}") String clientSecret) {
        return new IgdbCatalog(Clock.systemUTC(), clientId, clientSecret);
    }

    /** Open Library and Deezer need no key: fixed hosts, nothing to configure. */
    @Bean
    CatalogProvider openLibraryCatalog() {
        return new OpenLibraryCatalog(Clock.systemUTC());
    }

    @Bean
    CatalogProvider deezerCatalog() {
        return new DeezerCatalog(Clock.systemUTC());
    }

    /** Every {@link CatalogProvider} bean goes in; a new catalog is just a new bean. */
    @Bean
    CatalogRegistry catalogRegistry(List<CatalogProvider> providers) {
        return new CatalogRegistry(providers);
    }
}
