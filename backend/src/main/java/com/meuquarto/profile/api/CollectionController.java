package com.meuquarto.profile.api;

import com.meuquarto.media.application.MediaSummaries;
import com.meuquarto.profile.application.ProfileCollection;
import com.meuquarto.profile.domain.CollectionSort;
import com.meuquarto.shared.domain.MediaCategory;
import com.meuquarto.shared.security.CurrentUserId;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The signed-in owner's collection. Every route needs a session; the owner always comes from it. */
@RestController
@RequestMapping("/api/collection")
public class CollectionController {

    private final ProfileCollection collection;

    public CollectionController(ProfileCollection collection) {
        this.collection = collection;
    }

    @PostMapping
    public ResponseEntity<ItemResponse> add(@CurrentUserId long userId, @Valid @RequestBody AddRequest body) {
        ProfileCollection.AddResult result = collection.add(userId, body.category(), body.externalId());
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(ItemResponse.of(result.item()));
    }

    @GetMapping
    public ListResponse list(@CurrentUserId long userId,
            @RequestParam MediaCategory category,
            @RequestParam(defaultValue = "recent") String sort,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "24") @Min(1) @Max(ProfileCollection.MAX_PAGE_SIZE) int size,
            @RequestParam(defaultValue = "false") boolean includeHidden) {
        ProfileCollection.Listing listing =
                collection.list(userId, category, CollectionSort.parse(sort), page, size, includeHidden);
        return new ListResponse(listing.items().stream().map(ItemResponse::of).toList(), listing.page(),
                listing.size(), listing.total());
    }

    /** A field left out is not changed; {@code "note": ""} clears the note. */
    @PatchMapping("/{id}")
    public ItemResponse update(@CurrentUserId long userId, @PathVariable long id,
            @Valid @RequestBody UpdateRequest body) {
        return ItemResponse.of(collection.update(userId, id, body.note(), body.hidden()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@CurrentUserId long userId, @PathVariable long id) {
        collection.remove(userId, id);
    }

    public record AddRequest(
            @NotNull MediaCategory category,
            @NotNull @Size(min = 1, max = 64) @Pattern(regexp = "[A-Za-z0-9_-]+") String externalId) {}

    /** The 2000 limit only guards the body size; the 140-character rule is the domain's (NOTE_TOO_LONG). */
    public record UpdateRequest(@Size(max = 2000) String note, Boolean hidden) {}

    public record ListResponse(List<ItemResponse> items, int page, int size, long total) {}

    public record ItemResponse(long id, MediaCategory category, Media media, String note, boolean hidden,
            List<String> sources, boolean removable, Instant addedAt, Instant lastActivityAt) {

        static ItemResponse of(ProfileCollection.ItemView item) {
            return new ItemResponse(item.id(), item.category(), Media.of(item.media()), item.note(), item.hidden(),
                    item.sources(), item.removable(), item.addedAt(), item.lastActivityAt());
        }
    }

    public record Media(String provider, String externalId, String title, Integer year, String imageUrl) {

        static Media of(MediaSummaries.Summary media) {
            return new Media(media.provider(), media.externalId(), media.title(), media.year(), media.imageUrl());
        }
    }
}
