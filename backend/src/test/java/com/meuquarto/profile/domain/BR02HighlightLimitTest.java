package com.meuquarto.profile.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.meuquarto.profile.domain.HighlightException.Problem;
import com.meuquarto.shared.domain.ErrorKind;
import com.meuquarto.shared.domain.MediaCategory;
import java.time.Instant;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** BR-02: every highlight list holds 4 highlights, the same for everyone. No Spring context, no database. */
class BR02HighlightLimitTest {

    private static final long PROFILE = 1;
    private static final Instant NOW = Instant.parse("2026-10-01T10:00:00Z");

    private static CollectionItem item(long id, MediaCategory category) {
        CollectionItem item = CollectionItem.addedManually(PROFILE, 100 + id, category, NOW);
        ReflectionTestUtils.setField(item, "id", id);
        return item;
    }

    private static void fill(HighlightList list, int count) {
        IntStream.rangeClosed(1, count).forEach(i -> list.highlight(item(i, MediaCategory.MOVIE)));
    }

    private static Problem problemOf(Runnable action) {
        try {
            action.run();
        } catch (HighlightException e) {
            return e.problem();
        }
        throw new AssertionError("expected a HighlightException");
    }

    @Test
    void theLimitIsFour() {
        assertThat(HighlightList.LIMIT).isEqualTo(4);
    }

    @Test
    void aCategoryListAcceptsTheFourthHighlightAndRefusesTheFifth() {
        HighlightList list = HighlightList.forProfile(PROFILE, MediaCategory.MOVIE);

        fill(list, 4);

        assertThat(list.highlights()).hasSize(4);
        assertThat(problemOf(() -> list.highlight(item(5, MediaCategory.MOVIE))))
                .isEqualTo(Problem.HIGHLIGHT_LIMIT_REACHED);
        assertThat(list.highlights()).hasSize(4);
    }

    @Test
    void favoritesOfEverythingHaveTheSameLimit() {
        HighlightList favorites = HighlightList.forProfile(PROFILE, null);
        favorites.highlight(item(1, MediaCategory.MOVIE));
        favorites.highlight(item(2, MediaCategory.BOOK));
        favorites.highlight(item(3, MediaCategory.GAME));
        favorites.highlight(item(4, MediaCategory.MUSIC));

        assertThat(problemOf(() -> favorites.highlight(item(5, MediaCategory.ANIME))))
                .isEqualTo(Problem.HIGHLIGHT_LIMIT_REACHED);
    }

    @Test
    void reachingTheLimitIsAStateConflictNotABadInput() {
        assertThat(new HighlightException(Problem.HIGHLIGHT_LIMIT_REACHED).kind()).isEqualTo(ErrorKind.CONFLICT);
    }
}
