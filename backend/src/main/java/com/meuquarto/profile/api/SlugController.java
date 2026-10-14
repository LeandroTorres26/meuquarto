package com.meuquarto.profile.api;

import com.meuquarto.profile.application.SlugAvailability;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public: used by the sign-up form (S2) to check a slug while the user types. */
@RestController
@RequestMapping("/api/slugs")
public class SlugController {

    private final SlugAvailability availability;

    public SlugController(SlugAvailability availability) {
        this.availability = availability;
    }

    @GetMapping("/{slug}/availability")
    public SlugAvailabilityResponse availability(@PathVariable String slug) {
        SlugAvailability.Result result = availability.check(slug);
        return new SlugAvailabilityResponse(
                result.slug(), result.available(), result.reason() == null ? null : result.reason().name());
    }

    public record SlugAvailabilityResponse(String slug, boolean available, String reason) {}
}
