package com.meuquarto.profile.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.meuquarto.profile.domain.HighlightException.Problem;
import com.meuquarto.shared.domain.ErrorKind;
import com.meuquarto.shared.domain.MediaCategory;
import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** Highlight rules (BR-02, BR-07, FR-09a, FR-11) with no Spring context and no database. */
class HighlightListTest {

    private static final Instant NOW = Instant.parse("2026-10-01T10:00:00Z");
    private static final long PROFILE = 1;

    private final Plan free = new FreePlan();
    private final Plan pack = new PackPlan();

    private static CollectionItem item(long id, MediaCategory category) {
        CollectionItem item = CollectionItem.addedManually(PROFILE, 100 + id, category, NOW);
        ReflectionTestUtils.setField(item, "id", id);
        return item;
    }

    private static CollectionItem movie(long id) {
        return item(id, MediaCategory.MOVIE);
    }

    private static HighlightList movies() {
        return HighlightList.forProfile(PROFILE, MediaCategory.MOVIE);
    }

    /** Gives the new highlights the ids the database would give (500 + the item id). */
    private static void assignIds(HighlightList list) {
        for (Highlight highlight : list.highlights()) {
            if (highlight.id() == null) {
                ReflectionTestUtils.setField(highlight, "id", 500L + highlight.collectionItemId());
            }
        }
    }

    private static Problem problemOf(Runnable action) {
        try {
            action.run();
        } catch (HighlightException e) {
            return e.problem();
        }
        throw new AssertionError("expected a HighlightException");
    }

    private static List<Long> itemIds(HighlightList list) {
        return list.highlights().stream().map(Highlight::collectionItemId).toList();
    }

    private static List<Long> highlightIds(HighlightList list) {
        return list.highlights().stream().map(Highlight::id).toList();
    }

    private void fill(HighlightList list, Plan plan, int count) {
        IntStream.rangeClosed(1, count).forEach(i -> list.highlight(movie(i), plan));
    }

    @Test
    void fourthHighlightIsAcceptedOnTheFreePlan_BR02() {
        HighlightList list = movies();

        fill(list, free, 4);

        assertThat(list.highlights()).hasSize(4);
    }

    @Test
    void fifthHighlightIsRefusedOnTheFreePlan_BR02() {
        HighlightList list = movies();
        fill(list, free, 4);

        assertThat(problemOf(() -> list.highlight(movie(5), free))).isEqualTo(Problem.HIGHLIGHT_LIMIT_REACHED);
        assertThat(list.highlights()).hasSize(4);
    }

    @Test
    void twelveHighlightsAreAcceptedAndThirteenthRefusedOnThePackPlan_BR02() {
        HighlightList list = movies();

        fill(list, pack, 12);

        assertThat(list.highlights()).hasSize(12);
        assertThat(problemOf(() -> list.highlight(movie(13), pack))).isEqualTo(Problem.HIGHLIGHT_LIMIT_REACHED);
    }

    @Test
    void limitReachedIsAStateConflict_BR02() {
        assertThat(new HighlightException(Problem.HIGHLIGHT_LIMIT_REACHED).kind()).isEqualTo(ErrorKind.CONFLICT);
    }

    @Test
    void highlightsAreAppendedAtTheEnd() {
        HighlightList list = movies();

        fill(list, free, 3);

        assertThat(itemIds(list)).containsExactly(1L, 2L, 3L);
        assertThat(list.highlights()).extracting(Highlight::position).containsExactly(0, 1, 2);
    }

    @Test
    void theSameItemCannotBeHighlightedTwiceInOneList_BR07() {
        HighlightList list = movies();
        list.highlight(movie(1), free);

        assertThat(problemOf(() -> list.highlight(movie(1), free))).isEqualTo(Problem.ALREADY_HIGHLIGHTED);
    }

    @Test
    void aHiddenItemCannotBeHighlighted() {
        CollectionItem hidden = movie(1);
        hidden.hide();

        assertThat(problemOf(() -> movies().highlight(hidden, free))).isEqualTo(Problem.ITEM_HIDDEN);
    }

    @Test
    void anItemOfAnotherProfileCannotBeHighlighted() {
        CollectionItem foreign = CollectionItem.addedManually(2, 100, MediaCategory.MOVIE, NOW);
        ReflectionTestUtils.setField(foreign, "id", 9L);

        assertThat(problemOf(() -> movies().highlight(foreign, free))).isEqualTo(Problem.NOT_IN_COLLECTION);
    }

    @Test
    void aCategoryListRefusesAnItemOfAnotherCategory() {
        assertThat(problemOf(() -> movies().highlight(item(1, MediaCategory.BOOK), free)))
                .isEqualTo(Problem.CATEGORY_MISMATCH);
    }

    @Test
    void favoritesTakeItemsOfAnyCategoryAndShareTheSameLimit_FR11_BR02() {
        HighlightList favorites = HighlightList.forProfile(PROFILE, null);
        favorites.highlight(item(1, MediaCategory.MOVIE), free);
        favorites.highlight(item(2, MediaCategory.BOOK), free);
        favorites.highlight(item(3, MediaCategory.GAME), free);
        favorites.highlight(item(4, MediaCategory.MUSIC), free);

        assertThat(favorites.highlights()).hasSize(4);
        assertThat(problemOf(() -> favorites.highlight(item(5, MediaCategory.ANIME), free)))
                .isEqualTo(Problem.HIGHLIGHT_LIMIT_REACHED);
    }

    @Test
    void unhighlightingClosesTheGapAndFreesASlot_BR02() {
        HighlightList list = movies();
        fill(list, free, 4);
        assignIds(list);

        list.unhighlight(502L);

        assertThat(itemIds(list)).containsExactly(1L, 3L, 4L);
        assertThat(list.highlights()).extracting(Highlight::position).containsExactly(0, 1, 2);
        list.highlight(movie(5), free);
        assertThat(list.highlights()).hasSize(4);
    }

    @Test
    void unhighlightingAnUnknownHighlightIsRefused() {
        HighlightList list = movies();

        assertThat(problemOf(() -> list.unhighlight(77))).isEqualTo(Problem.HIGHLIGHT_NOT_FOUND);
    }

    @Test
    void droppingAnItemRemovesItAndRenumbers() {
        HighlightList list = movies();
        fill(list, free, 3);

        list.dropItem(1);
        list.dropItem(999);

        assertThat(itemIds(list)).containsExactly(2L, 3L);
        assertThat(list.highlights()).extracting(Highlight::position).containsExactly(0, 1);
    }

    @Test
    void reorderRewritesPositionsFromZero() {
        HighlightList list = movies();
        fill(list, free, 3);
        assignIds(list);

        list.reorder(List.of(503L, 501L, 502L));

        assertThat(itemIds(list)).containsExactly(3L, 1L, 2L);
        assertThat(list.highlights()).extracting(Highlight::position).containsExactly(0, 1, 2);
    }

    @Test
    void reorderRefusesAnythingButExactlyTheCurrentSet() {
        HighlightList list = movies();
        fill(list, free, 3);
        assignIds(list);

        assertThat(problemOf(() -> list.reorder(List.of(501L, 502L)))).isEqualTo(Problem.INVALID_ORDER);
        assertThat(problemOf(() -> list.reorder(List.of(501L, 502L, 503L, 504L)))).isEqualTo(Problem.INVALID_ORDER);
        assertThat(problemOf(() -> list.reorder(List.of(501L, 502L, 999L)))).isEqualTo(Problem.INVALID_ORDER);
        assertThat(problemOf(() -> list.reorder(List.of(501L, 501L, 502L)))).isEqualTo(Problem.INVALID_ORDER);
        assertThat(itemIds(list)).containsExactly(1L, 2L, 3L);
        assertThat(highlightIds(list)).containsExactly(501L, 502L, 503L);
    }
}
