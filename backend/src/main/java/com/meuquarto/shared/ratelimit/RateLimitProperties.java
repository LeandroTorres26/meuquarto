package com.meuquarto.shared.ratelimit;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Per-IP limits ({@code meuquarto.rate-limit.*}). Defaults are the production values. */
@ConfigurationProperties("meuquarto.rate-limit")
public record RateLimitProperties(
        @DefaultValue Rule signup,
        @DefaultValue Rule login,
        @DefaultValue Rule slugAvailability,
        @DefaultValue Rule catalogSearch,
        @DefaultValue Rule collectionAdd,
        @DefaultValue Rule linkAccount,
        @DefaultValue Rule manualSync,
        @DefaultValue Rule avatarUpload,
        @DefaultValue Rule publicRead,
        @DefaultValue Rule anonymous) {

    public RateLimitProperties {
        signup = signup.orElse(10, Duration.ofHours(1));
        login = login.orElse(30, Duration.ofMinutes(15));
        slugAvailability = slugAvailability.orElse(60, Duration.ofMinutes(1));
        catalogSearch = catalogSearch.orElse(60, Duration.ofMinutes(1));
        collectionAdd = collectionAdd.orElse(30, Duration.ofMinutes(1));
        linkAccount = linkAccount.orElse(10, Duration.ofMinutes(10));
        manualSync = manualSync.orElse(30, Duration.ofMinutes(10));
        avatarUpload = avatarUpload.orElse(20, Duration.ofMinutes(10));
        publicRead = publicRead.orElse(120, Duration.ofMinutes(1));
        anonymous = anonymous.orElse(300, Duration.ofMinutes(1));
    }

    /** At most {@code limit} requests per {@code window}; unset values fall back to the defaults. */
    public record Rule(Integer limit, Duration window) {

        Rule orElse(int defaultLimit, Duration defaultWindow) {
            return new Rule(limit == null ? defaultLimit : limit, window == null ? defaultWindow : window);
        }
    }
}
