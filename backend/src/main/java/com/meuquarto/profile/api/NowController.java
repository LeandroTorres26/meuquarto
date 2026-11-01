package com.meuquarto.profile.api;

import com.meuquarto.profile.application.ProfileNow;
import com.meuquarto.profile.application.PublicProfile;
import com.meuquarto.profile.domain.NowMode;
import com.meuquarto.shared.domain.MediaCategory;
import com.meuquarto.shared.security.CurrentUserId;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Instant;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The signed-in owner's "Agora" settings (S10). Every route needs a session; the owner always comes from it.
 * The category is a path variable and is validated by the enum conversion.
 */
@RestController
@RequestMapping("/api/now")
public class NowController {

    private final ProfileNow now;

    public NowController(ProfileNow now) {
        this.now = now;
    }

    /** The seven categories, in order, each with its mode and what the profile shows. */
    @GetMapping
    public List<EntryResponse> overview(@CurrentUserId long userId) {
        return now.overview(userId).stream().map(EntryResponse::of).toList();
    }

    /** Automatic, pinned (with {@code collectionItemId}) or hidden. */
    @PutMapping("/{category}")
    public EntryResponse change(@CurrentUserId long userId, @PathVariable MediaCategory category,
            @Valid @RequestBody ChangeRequest body) {
        return EntryResponse.of(now.change(userId, category, body.mode(), body.collectionItemId()));
    }

    /** {@code collectionItemId} is required for {@code PINNED} and refused for the other modes. */
    public record ChangeRequest(@NotNull NowMode mode, @Positive Long collectionItemId) {}

    public record EntryResponse(MediaCategory category, NowMode mode, boolean automaticAvailable, ShownResponse shown) {

        static EntryResponse of(ProfileNow.Entry entry) {
            return new EntryResponse(entry.category(), entry.mode(), entry.automaticAvailable(),
                    entry.shown() == null ? null : ShownResponse.of(entry.shown()));
        }
    }

    /** {@code source} is {@code MANUAL} for a pin, else the platform name; {@code at} is null for a pin. */
    public record ShownResponse(long collectionItemId, String title, Integer year, String imageUrl, String source,
            Instant at) {

        static ShownResponse of(PublicProfile.NowItem item) {
            return new ShownResponse(item.ref(), item.title(), item.year(), item.imageUrl(), item.source(), item.at());
        }
    }
}
