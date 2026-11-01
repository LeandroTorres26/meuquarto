package com.meuquarto.profile.api;

import com.meuquarto.profile.application.PublicProfile;
import com.meuquarto.profile.application.PublicProfiles;
import com.meuquarto.profile.domain.CollectionSort;
import com.meuquarto.shared.domain.MediaCategory;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public, read-only view of a profile (S4, S17, S18). No session: the routes are open for GET only, and
 * nothing here depends on who asks. Every response is built from local data, never from a provider.
 */
@RestController
@RequestMapping("/api/public/profiles")
public class PublicProfileController {

    private final PublicProfiles profiles;

    public PublicProfileController(PublicProfiles profiles) {
        this.profiles = profiles;
    }

    @GetMapping("/{slug}")
    public ProfileResponse profile(@PathVariable String slug) {
        return ProfileResponse.of(profiles.profile(slug));
    }

    @GetMapping("/{slug}/items/{ref}")
    public DetailResponse item(@PathVariable String slug, @PathVariable String ref) {
        return DetailResponse.of(profiles.item(slug, ref));
    }

    @GetMapping("/{slug}/collection")
    public ListingResponse collection(@PathVariable String slug,
            @RequestParam MediaCategory category,
            @RequestParam(defaultValue = "recent") String sort,
            @RequestParam(required = false) @Size(max = PublicProfiles.MAX_QUERY_LENGTH) String q,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "24") @Min(1) @Max(PublicProfiles.MAX_PAGE_SIZE) int size) {
        return ListingResponse.of(profiles.listing(slug, category, CollectionSort.parse(sort), q, page, size));
    }

    public record ProfileResponse(String slug, String displayName, List<NowResponse> now,
            List<ItemResponse> favorites, List<SectionResponse> categories) {

        static ProfileResponse of(PublicProfile profile) {
            return new ProfileResponse(profile.slug(), profile.displayName(),
                    profile.now().stream().map(NowResponse::of).toList(),
                    profile.favorites().stream().map(ItemResponse::of).toList(),
                    profile.categories().stream().map(SectionResponse::of).toList());
        }
    }

    /**
     * One "Agora" entry (FR-15): {@code source} is {@code MANUAL} for a pin, else the platform name;
     * {@code at} is when it happened there (null for a pin).
     */
    public record NowResponse(long ref, MediaCategory category, String title, Integer year, String imageUrl,
            String source, Instant at) {

        static NowResponse of(PublicProfile.NowItem item) {
            return new NowResponse(item.ref(), item.category(), item.title(), item.year(), item.imageUrl(),
                    item.source(), item.at());
        }
    }

    /** {@code total} counts the visible collection of the category; {@code mostRecent} marks the no-highlight fallback. */
    public record SectionResponse(MediaCategory category, long total, boolean mostRecent, List<ItemResponse> items) {

        static SectionResponse of(PublicProfile.Section section) {
            return new SectionResponse(section.category(), section.total(), section.mostRecent(),
                    section.items().stream().map(ItemResponse::of).toList());
        }
    }

    public record ItemResponse(long ref, MediaCategory category, String title, Integer year, String imageUrl,
            String note) {

        static ItemResponse of(PublicProfile.PublicItem item) {
            return new ItemResponse(item.ref(), item.category(), item.title(), item.year(), item.imageUrl(),
                    item.note());
        }
    }

    public record DetailResponse(ItemResponse item, List<OriginResponse> origin, List<LinkResponse> links) {

        static DetailResponse of(PublicProfile.Detail detail) {
            return new DetailResponse(ItemResponse.of(detail.item()),
                    detail.origin().stream().map(o -> new OriginResponse(o.source(), o.firstSeenAt())).toList(),
                    detail.links().stream().map(l -> new LinkResponse(l.provider(), l.url())).toList());
        }
    }

    public record OriginResponse(String source, Instant firstSeenAt) {}

    public record LinkResponse(String provider, String url) {}

    public record ListingResponse(List<ListingItemResponse> items, int page, int size, long total) {

        static ListingResponse of(PublicProfile.Listing listing) {
            return new ListingResponse(listing.items().stream().map(ListingItemResponse::of).toList(),
                    listing.page(), listing.size(), listing.total());
        }
    }

    public record ListingItemResponse(long ref, MediaCategory category, String title, Integer year, String imageUrl,
            String note, boolean highlighted) {

        static ListingItemResponse of(PublicProfile.Entry entry) {
            PublicProfile.PublicItem item = entry.item();
            return new ListingItemResponse(item.ref(), item.category(), item.title(), item.year(), item.imageUrl(),
                    item.note(), entry.highlighted());
        }
    }
}
