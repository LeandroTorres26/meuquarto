package com.meuquarto.media.domain;

/** A catalog search text: trimmed, 2 to 100 characters. Anything else is rejected before any provider is called. */
public record SearchQuery(String text) {

    public static final int MIN_LENGTH = 2;
    public static final int MAX_LENGTH = 100;

    public SearchQuery {
        if (text == null || text.codePointCount(0, text.length()) < MIN_LENGTH
                || text.codePointCount(0, text.length()) > MAX_LENGTH) {
            throw new InvalidSearchQueryException();
        }
    }

    public static SearchQuery of(String raw) {
        return new SearchQuery(raw == null ? null : raw.strip());
    }
}
