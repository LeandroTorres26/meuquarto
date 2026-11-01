package com.meuquarto.profile.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meuquarto.profile.domain.CollectionException.Problem;
import com.meuquarto.shared.domain.ErrorKind;
import com.meuquarto.shared.domain.MediaCategory;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** Collection rules (BR-07, FR-09, FR-10) with no Spring and no database. */
class CollectionItemTest {

    private static final Instant T0 = Instant.parse("2026-10-01T10:00:00Z");
    private static final Instant T1 = Instant.parse("2026-10-02T10:00:00Z");

    private static CollectionItem manualItem() {
        return CollectionItem.addedManually(1, 10, MediaCategory.MOVIE, T0);
    }

    private static CollectionItem syncedItem() {
        return CollectionItem.foundBySync(1, 10, MediaCategory.MOVIE, "LETTERBOXD", 5L, T0);
    }

    private static Problem problemOf(Runnable action) {
        try {
            action.run();
        } catch (CollectionException e) {
            return e.problem();
        }
        throw new AssertionError("expected a CollectionException");
    }

    @Test
    void anItemAddedByHandStartsVisibleWithOneManualSource() {
        CollectionItem item = manualItem();

        assertThat(item.hidden()).isFalse();
        assertThat(item.sources()).extracting(ItemSource::source).containsExactly("MANUAL");
        assertThat(item.addedAt()).isEqualTo(T0);
        assertThat(item.lastActivityAt()).isEqualTo(T0);
    }

    @Test
    void br07AddingTheSameWorkAgainKeepsOneSourceAndShowsAHiddenItemAgain() {
        CollectionItem item = manualItem();
        item.hide();

        item.addAgainByHand(T1);

        assertThat(item.hidden()).isFalse();
        assertThat(item.sources()).hasSize(1);
        assertThat(item.addedAt()).isEqualTo(T0);
        assertThat(item.lastActivityAt()).isEqualTo(T1);
    }

    @Test
    void br07AWorkFromSyncAndByHandIsOneItemWithTwoSources() {
        CollectionItem item = syncedItem();

        item.addAgainByHand(T1);

        assertThat(item.sources()).extracting(ItemSource::source).containsExactlyInAnyOrder("LETTERBOXD", "MANUAL");
    }

    @Test
    void fr09ManualOnlyItemsCanBeRemoved() {
        CollectionItem item = manualItem();

        assertThat(item.isRemovable()).isTrue();
        item.ensureRemovable();
    }

    @Test
    void fr09ItemsFromASyncCannotBeRemovedOnlyHidden() {
        CollectionItem item = syncedItem();

        assertThat(item.isRemovable()).isFalse();
        assertThat(problemOf(item::ensureRemovable)).isEqualTo(Problem.CANNOT_REMOVE_SYNCED);
        item.hide();
        assertThat(item.hidden()).isTrue();
    }

    @Test
    void fr09AnItemAlsoAddedByHandStaysUnremovableWhileASyncSourceExists() {
        CollectionItem item = syncedItem();
        item.addAgainByHand(T1);

        assertThat(problemOf(item::ensureRemovable)).isEqualTo(Problem.CANNOT_REMOVE_SYNCED);
    }

    @Test
    void fr09AHiddenItemStaysHiddenWhenASyncSeesItAgain() {
        CollectionItem item = syncedItem();
        item.hide();

        item.seenBySync("LETTERBOXD", 5L, T1);

        assertThat(item.hidden()).isTrue();
        assertThat(item.lastActivityAt()).isEqualTo(T1);
        assertThat(item.sources()).hasSize(1);
    }

    @Test
    void aSyncSourceCanNeverBeNamedLikeTheManualMarker() {
        assertThatThrownBy(() -> ItemSource.synced("MANUAL", null, T0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ItemSource.synced(" ", null, T0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void hidingAndUnhidingAreReversible() {
        CollectionItem item = manualItem();

        item.hide();
        assertThat(item.hidden()).isTrue();
        item.unhide();
        assertThat(item.hidden()).isFalse();
    }

    @Test
    void fr10NoteIsTrimmedAndStoredAsPlainText() {
        CollectionItem item = manualItem();

        item.changeNote("   <b>otimo</b> & \"bom\"  ");

        assertThat(item.note()).isEqualTo("<b>otimo</b> & \"bom\"");
    }

    @Test
    void fr10ABlankNoteClearsIt() {
        CollectionItem item = manualItem();
        item.changeNote("vale a pena");

        item.changeNote("   ");

        assertThat(item.note()).isNull();
        item.changeNote("outra");
        item.changeNote(null);
        assertThat(item.note()).isNull();
    }

    @Test
    void fr10NoteAllows140CharactersAndRejects141() {
        CollectionItem item = manualItem();

        item.changeNote("a".repeat(140));
        assertThat(item.note()).hasSize(140);
        assertThat(problemOf(() -> item.changeNote("a".repeat(141)))).isEqualTo(Problem.NOTE_TOO_LONG);
        assertThat(item.note()).hasSize(140);
    }

    @Test
    void fr10NoteLengthCountsCharactersNotUtf16Units() {
        CollectionItem item = manualItem();

        item.changeNote("🎬".repeat(140));

        assertThat(item.note().codePointCount(0, item.note().length())).isEqualTo(140);
    }

    @Test
    void fr10NoteWithControlCharactersIsRejected() {
        CollectionItem item = manualItem();

        assertThat(problemOf(() -> item.changeNote("linha1\nlinha2"))).isEqualTo(Problem.NOTE_INVALID);
        assertThat(problemOf(() -> item.changeNote("nul\u0000"))).isEqualTo(Problem.NOTE_INVALID);
    }

    // ---- FR-16: unlinking an account ----

    @Test
    void fr16AnItemOnlyFromTheAccountLeavesTheCollection() {
        CollectionItem item = syncedItem();

        assertThat(item.releaseAccount(5L, false, T1)).isTrue();
    }

    @Test
    void fr16AHighlightedItemStaysAsManual() {
        CollectionItem item = syncedItem();

        assertThat(item.releaseAccount(5L, true, T1)).isFalse();
        assertThat(item.sources()).extracting(ItemSource::source).containsExactly("MANUAL");
        assertThat(item.isRemovable()).isTrue();
    }

    @Test
    void fr16ANotedItemStaysAsManual() {
        CollectionItem item = syncedItem();
        item.changeNote("para rever");

        assertThat(item.releaseAccount(5L, false, T1)).isFalse();
        assertThat(item.sources()).extracting(ItemSource::source).containsExactly("MANUAL");
    }

    @Test
    void fr16AnItemWithAnotherSourceKeepsIt() {
        CollectionItem item = syncedItem();
        item.addAgainByHand(T1);

        assertThat(item.releaseAccount(5L, false, T1)).isFalse();
        assertThat(item.sources()).extracting(ItemSource::source).containsExactly("MANUAL");
    }

    @Test
    void fr16AnItemFromAnotherAccountIsNotTouched() {
        CollectionItem item = syncedItem();

        assertThat(item.releaseAccount(99L, false, T1)).isFalse();
        assertThat(item.sources()).extracting(ItemSource::source).containsExactly("LETTERBOXD");
    }

    @Test
    void fr16AManualItemIsNotTouched() {
        CollectionItem item = manualItem();

        assertThat(item.releaseAccount(5L, false, T1)).isFalse();
        assertThat(item.sources()).extracting(ItemSource::source).containsExactly("MANUAL");
    }

    @Test
    void sortParsingIsCaseInsensitiveAndDefaultsToRecent() {
        assertThat(CollectionSort.parse("title")).isEqualTo(CollectionSort.TITLE);
        assertThat(CollectionSort.parse("YEAR")).isEqualTo(CollectionSort.YEAR);
        assertThat(CollectionSort.parse(null)).isEqualTo(CollectionSort.RECENT);
        assertThat(problemOf(() -> CollectionSort.parse("random"))).isEqualTo(Problem.INVALID_SORT);
    }

    @Test
    void everyProblemHasAKindThatTheApiCanMap() {
        assertThat(new CollectionException(Problem.ITEM_NOT_FOUND).kind()).isEqualTo(ErrorKind.NOT_FOUND);
        assertThat(new CollectionException(Problem.CANNOT_REMOVE_SYNCED).kind()).isEqualTo(ErrorKind.CONFLICT);
        assertThat(new CollectionException(Problem.CATALOG_UNAVAILABLE).kind()).isEqualTo(ErrorKind.UNAVAILABLE);
        assertThat(new CollectionException(Problem.NOTE_TOO_LONG).code()).isEqualTo("NOTE_TOO_LONG");
    }

    @Test
    void theSyncedProviderIsTheMostRecentNonManualSourceAndEmptyForManualItems() {
        assertThat(manualItem().syncedProvider()).isEmpty();

        CollectionItem item = syncedItem();
        item.addAgainByHand(T1);
        assertThat(item.syncedProvider()).contains("LETTERBOXD");

        item.seenBySync("TMDB_IMPORT", 6L, T1);
        assertThat(item.syncedProvider()).contains("TMDB_IMPORT");
    }
}
