package com.meuquarto.media.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meuquarto.shared.domain.MediaCategory;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CatalogDomainTest {

    /** Fake catalog: shows the registry works with any class behind the interface. */
    private record FakeCatalog(String id, MediaCategory served) implements CatalogProvider {

        @Override
        public boolean serves(MediaCategory category) {
            return category == served;
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public List<CatalogEntry> search(MediaCategory category, String query) {
            return List.of(new CatalogEntry(category, id, "1", query, null, null, false));
        }

        @Override
        public Optional<CatalogEntry> fetch(MediaCategory category, String externalId) {
            return Optional.empty();
        }
    }

    @Test
    void registryAsksEachProviderWhichCategoryItServes() {
        CatalogRegistry registry = new CatalogRegistry(List.of(
                new FakeCatalog("A", MediaCategory.MOVIE), new FakeCatalog("B", MediaCategory.BOOK)));

        assertThat(registry.forCategory(MediaCategory.BOOK).orElseThrow().id()).isEqualTo("B");
        assertThat(registry.forCategory(MediaCategory.MOVIE).orElseThrow().id()).isEqualTo("A");
        assertThat(registry.forCategory(MediaCategory.GAME)).isEmpty();
    }

    @Test
    void sameCategoryProviderAndIdIsTheSameMediaKey_BR04() {
        MediaKey a = new MediaKey(MediaCategory.MOVIE, "TMDB", "603");
        MediaKey b = new CatalogEntry(MediaCategory.MOVIE, "TMDB", "603", "Matrix", 1999, null, false).key();

        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
        assertThat(a).isNotEqualTo(new MediaKey(MediaCategory.SERIES, "TMDB", "603"));
        assertThatThrownBy(() -> new MediaKey(MediaCategory.MOVIE, "TMDB", " ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void searchQueryIsTrimmedAndBetween2And100Characters() {
        assertThat(SearchQuery.of("  matrix  ").text()).isEqualTo("matrix");
        assertThat(SearchQuery.of("ab").text()).isEqualTo("ab");
        assertThat(SearchQuery.of("a".repeat(100)).text()).hasSize(100);

        assertThatThrownBy(() -> SearchQuery.of(" a ")).isInstanceOf(InvalidSearchQueryException.class);
        assertThatThrownBy(() -> SearchQuery.of("   ")).isInstanceOf(InvalidSearchQueryException.class);
        assertThatThrownBy(() -> SearchQuery.of(null)).isInstanceOf(InvalidSearchQueryException.class);
        assertThatThrownBy(() -> SearchQuery.of("a".repeat(101))).isInstanceOf(InvalidSearchQueryException.class);
    }
}
