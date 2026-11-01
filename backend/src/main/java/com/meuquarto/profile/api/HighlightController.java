package com.meuquarto.profile.api;

import com.meuquarto.media.application.MediaSummaries;
import com.meuquarto.profile.application.ProfileHighlights;
import com.meuquarto.shared.domain.MediaCategory;
import com.meuquarto.shared.security.CurrentUserId;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The signed-in owner's highlight lists. Every route needs a session; the owner always comes from it.
 * {@code category: null} means the "Favoritos de tudo" list.
 */
@RestController
@RequestMapping("/api/highlights")
public class HighlightController {

    /** The most highlights any plan allows is far below this; it only bounds the body size. */
    private static final int MAX_IDS = 100;

    private final ProfileHighlights highlights;

    public HighlightController(ProfileHighlights highlights) {
        this.highlights = highlights;
    }

    @GetMapping
    public OverviewResponse overview(@CurrentUserId long userId) {
        ProfileHighlights.Overview overview = highlights.overview(userId);
        return new OverviewResponse(overview.plan(), overview.limit(),
                overview.lists().stream().map(ListResponse::of).toList());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public HighlightResponse add(@CurrentUserId long userId, @Valid @RequestBody AddRequest body) {
        return HighlightResponse.of(highlights.add(userId, body.category(), body.collectionItemId()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@CurrentUserId long userId, @PathVariable long id) {
        highlights.remove(userId, id);
    }

    @PutMapping("/order")
    public ListResponse reorder(@CurrentUserId long userId, @Valid @RequestBody OrderRequest body) {
        return ListResponse.of(highlights.reorder(userId, body.category(), body.ids()));
    }

    /** A {@code null} category is the favorites list. */
    public record AddRequest(MediaCategory category, @NotNull @Positive Long collectionItemId) {}

    public record OrderRequest(MediaCategory category,
            @NotNull @Size(max = MAX_IDS) List<@NotNull @Positive Long> ids) {}

    public record OverviewResponse(String plan, int limit, List<ListResponse> lists) {}

    public record ListResponse(MediaCategory category, List<HighlightResponse> items) {

        static ListResponse of(ProfileHighlights.ListView list) {
            return new ListResponse(list.category(), list.items().stream().map(HighlightResponse::of).toList());
        }
    }

    public record HighlightResponse(long id, long collectionItemId, int position, Media media) {

        static HighlightResponse of(ProfileHighlights.HighlightView view) {
            return new HighlightResponse(view.id(), view.collectionItemId(), view.position(), Media.of(view.media()));
        }
    }

    public record Media(String provider, String externalId, String title, Integer year, String imageUrl) {

        static Media of(MediaSummaries.Summary media) {
            return new Media(media.provider(), media.externalId(), media.title(), media.year(), media.imageUrl());
        }
    }
}
