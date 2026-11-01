package com.meuquarto.profile.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meuquarto.profile.domain.NowBlock.Shown;
import com.meuquarto.profile.domain.NowException.Problem;
import com.meuquarto.shared.domain.MediaCategory;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** BR-06 ("Agora" shows the latest synced item unless the owner pinned or hid it) with no Spring and no database. */
class BR06NowBlockTest {

    private static final long PROFILE = 7;
    private static final Instant NOW = Instant.parse("2026-10-01T12:00:00Z");

    private static CollectionItem item(long profile, MediaCategory category) {
        CollectionItem item = CollectionItem.addedManually(profile, 100, category, NOW);
        ReflectionTestUtils.setField(item, "id", 55L);
        return item;
    }

    private static NowEntry pinned(MediaCategory category) {
        NowEntry entry = NowEntry.automatic(PROFILE, category);
        entry.change(NowMode.PINNED, item(PROFILE, category));
        return entry;
    }

    private static NowEntry hidden(MediaCategory category) {
        NowEntry entry = NowEntry.automatic(PROFILE, category);
        entry.change(NowMode.HIDDEN, null);
        return entry;
    }

    @Test
    void theLatestSyncedItemIsShownWhenTheOwnerChangedNothing() {
        assertThat(NowBlock.shown(MediaCategory.MOVIE, null, false, Optional.of(9L)))
                .contains(new Shown(9, NowSource.AUTO));
    }

    @Test
    void theLatestSyncedItemIsShownForAnAutomaticEntryToo() {
        NowEntry entry = NowEntry.automatic(PROFILE, MediaCategory.ANIME);

        assertThat(NowBlock.shown(MediaCategory.ANIME, entry, false, Optional.of(9L)))
                .contains(new Shown(9, NowSource.AUTO));
    }

    @Test
    void nothingIsShownWhenNothingWasSynced() {
        assertThat(NowBlock.shown(MediaCategory.GAME, null, false, Optional.empty())).isEmpty();
    }

    @Test
    void aPinnedItemWinsOverTheLatestSyncedOne() {
        assertThat(NowBlock.shown(MediaCategory.MOVIE, pinned(MediaCategory.MOVIE), true, Optional.of(9L)))
                .contains(new Shown(55, NowSource.MANUAL));
    }

    @Test
    void aHiddenCategoryShowsNothingEvenWithSyncedItems() {
        assertThat(NowBlock.shown(MediaCategory.MUSIC, hidden(MediaCategory.MUSIC), false, Optional.of(9L))).isEmpty();
    }

    @Test
    void aPinThatCannotBeShownAnymoreFallsBackToTheAutomaticItem() {
        assertThat(NowBlock.shown(MediaCategory.MOVIE, pinned(MediaCategory.MOVIE), false, Optional.of(9L)))
                .contains(new Shown(9, NowSource.AUTO));
    }

    @Test
    void seriesIsManualOnlyAndNeverShowsAnAutomaticItem() {
        assertThat(NowBlock.hasAutomaticSource(MediaCategory.SERIES)).isFalse();
        assertThat(NowBlock.shown(MediaCategory.SERIES, null, false, Optional.of(9L))).isEmpty();
        assertThat(NowBlock.shown(MediaCategory.SERIES, pinned(MediaCategory.SERIES), true, Optional.of(9L)))
                .contains(new Shown(55, NowSource.MANUAL));
    }

    @Test
    void everyOtherCategoryHasAnAutomaticSource() {
        for (MediaCategory category : MediaCategory.values()) {
            assertThat(NowBlock.hasAutomaticSource(category)).isEqualTo(category != MediaCategory.SERIES);
        }
    }

    // --- the owner's choice (NowEntry) ---

    @Test
    void pinningAnotherCategorysItemIsRefused() {
        NowEntry entry = NowEntry.automatic(PROFILE, MediaCategory.MOVIE);

        assertThatThrownBy(() -> entry.change(NowMode.PINNED, item(PROFILE, MediaCategory.BOOK)))
                .isInstanceOfSatisfying(NowException.class, e -> assertThat(e.problem()).isEqualTo(Problem.CATEGORY_MISMATCH));
    }

    @Test
    void pinningSomeoneElsesItemIsRefusedAsNotFound() {
        NowEntry entry = NowEntry.automatic(PROFILE, MediaCategory.MOVIE);

        assertThatThrownBy(() -> entry.change(NowMode.PINNED, item(PROFILE + 1, MediaCategory.MOVIE)))
                .isInstanceOfSatisfying(NowException.class, e -> assertThat(e.problem()).isEqualTo(Problem.NOT_IN_COLLECTION));
    }

    @Test
    void pinningAHiddenItemIsRefused() {
        NowEntry entry = NowEntry.automatic(PROFILE, MediaCategory.MOVIE);
        CollectionItem item = item(PROFILE, MediaCategory.MOVIE);
        item.hide();

        assertThatThrownBy(() -> entry.change(NowMode.PINNED, item))
                .isInstanceOfSatisfying(NowException.class, e -> assertThat(e.problem()).isEqualTo(Problem.ITEM_HIDDEN));
    }

    @Test
    void pinningNeedsAnItemAndTheOtherModesRefuseOne() {
        NowEntry entry = NowEntry.automatic(PROFILE, MediaCategory.MOVIE);

        assertThatThrownBy(() -> entry.change(NowMode.PINNED, null))
                .isInstanceOfSatisfying(NowException.class, e -> assertThat(e.problem()).isEqualTo(Problem.ITEM_REQUIRED));
        assertThatThrownBy(() -> entry.change(NowMode.HIDDEN, item(PROFILE, MediaCategory.MOVIE)))
                .isInstanceOfSatisfying(NowException.class, e -> assertThat(e.problem()).isEqualTo(Problem.UNEXPECTED_ITEM));
        assertThatThrownBy(() -> entry.change(NowMode.AUTOMATIC, item(PROFILE, MediaCategory.MOVIE)))
                .isInstanceOfSatisfying(NowException.class, e -> assertThat(e.problem()).isEqualTo(Problem.UNEXPECTED_ITEM));
    }

    @Test
    void theModesAreExclusive() {
        NowEntry entry = NowEntry.automatic(PROFILE, MediaCategory.MOVIE);
        assertThat(entry.mode()).isEqualTo(NowMode.AUTOMATIC);

        entry.change(NowMode.PINNED, item(PROFILE, MediaCategory.MOVIE));
        assertThat(entry.mode()).isEqualTo(NowMode.PINNED);
        assertThat(entry.collectionItemId()).isEqualTo(55L);

        entry.change(NowMode.HIDDEN, null);
        assertThat(entry.mode()).isEqualTo(NowMode.HIDDEN);
        assertThat(entry.collectionItemId()).isNull();

        entry.change(NowMode.AUTOMATIC, null);
        assertThat(entry.mode()).isEqualTo(NowMode.AUTOMATIC);
        assertThat(entry.hidden()).isFalse();
    }

    @Test
    void hidingThePinnedItemPutsTheCategoryBackOnAutomatic() {
        NowEntry entry = pinned(MediaCategory.MOVIE);

        entry.releaseItem(999);
        assertThat(entry.mode()).isEqualTo(NowMode.PINNED);

        entry.releaseItem(55);
        assertThat(entry.mode()).isEqualTo(NowMode.AUTOMATIC);
        assertThat(entry.collectionItemId()).isNull();
    }
}
