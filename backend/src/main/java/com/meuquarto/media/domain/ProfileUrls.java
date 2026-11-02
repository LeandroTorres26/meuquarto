package com.meuquarto.media.domain;

import com.meuquarto.shared.domain.ValidationRule;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Where the profile of a linked account lives on its platform (FR-12a, ADR 0023). The host and the path
 * are fixed here, per platform, and only the stored username is appended after it is checked and
 * percent-encoded, so a username can never turn into a link to somewhere else. A URL typed by the user
 * is never stored or shown. A name that would not be safe in a path simply has no link.
 * <p>
 * Also holds the format rules of the link-only platforms (Backloggd, Trakt, StoryGraph), which have no
 * provider class: the name is only checked for format, never looked up (ADR 0023, decision 5).
 */
public final class ProfileUrls {

    /** Letters, digits, underscore, dot, hyphen; never only dots (that would be {@code .} or {@code ..} in a path). */
    private static final String LINK_ONLY_CHARACTERS = "(?!\\.+\\z)[A-Za-z0-9_.-]+";
    private static final Pattern SAFE_NAME = Pattern.compile(LINK_ONLY_CHARACTERS);
    private static final int MAX_NAME = 64;

    private ProfileUrls() {}

    /** The profile link of {@code username} on {@code platform}; empty when the name is not a plain, safe token. */
    public static Optional<String> urlFor(ProviderId platform, String username) {
        if (platform == null || username == null || username.length() > MAX_NAME
                || !SAFE_NAME.matcher(username).matches()) {
            return Optional.empty();
        }
        String name = URLEncoder.encode(username, StandardCharsets.UTF_8);
        return Optional.of(switch (platform) {
            case LETTERBOXD -> "https://letterboxd.com/" + name + "/";
            case ANILIST -> "https://anilist.co/user/" + name + "/";
            case MYANIMELIST -> "https://myanimelist.net/profile/" + name;
            case LASTFM -> "https://www.last.fm/user/" + name;
            case STEAM -> "https://steamcommunity.com/id/" + name;
            case GOODREADS -> "https://www.goodreads.com/user/show/" + name;
            case BACKLOGGD -> "https://backloggd.com/u/" + name + "/";
            case TRAKT -> "https://trakt.tv/users/" + name;
            case STORYGRAPH -> "https://app.thestorygraph.com/profile/" + name;
        });
    }

    /** Format rules for the username of a link-only platform: 1 to 40 conservative characters. */
    public static List<ValidationRule<String, UsernameProblem>> linkOnlyUsernameRules() {
        return UsernameRules.of(1, 40, LINK_ONLY_CHARACTERS);
    }
}
