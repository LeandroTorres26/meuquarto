package com.meuquarto.media.infra;

import static org.assertj.core.api.Assertions.assertThat;

import com.meuquarto.media.domain.ActivityRegistry;
import com.meuquarto.media.domain.CatalogRegistry;
import com.meuquarto.media.domain.ProviderId;
import com.meuquarto.shared.domain.MediaCategory;
import com.meuquarto.support.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;

/** The real wiring (no stub): one AniList object, found by both registries. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class AniListWiringTests {

    @Autowired
    ApplicationContext context;

    @Autowired
    CatalogRegistry catalogs;

    @Autowired
    ActivityRegistry activities;

    @Test
    void theSameInstanceIsInBothRegistries() {
        AniListProvider bean = context.getBean(AniListProvider.class);

        assertThat(context.getBeansOfType(AniListProvider.class)).hasSize(1);
        assertThat(activities.find(ProviderId.ANILIST)).containsSame(bean);
        assertThat(catalogs.forCategory(MediaCategory.ANIME)).containsSame(bean);
        assertThat(catalogs.forCategory(MediaCategory.MANGA)).containsSame(bean);
        assertThat(catalogs.forCategory(MediaCategory.MOVIE).orElseThrow()).isNotSameAs(bean); // TMDB keeps its categories
    }
}
