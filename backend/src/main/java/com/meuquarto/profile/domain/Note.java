package com.meuquarto.profile.domain;

import com.meuquarto.profile.domain.CollectionException.Problem;

/**
 * The optional personal note of a collection item (FR-10): short plain text, kept as typed after
 * trimming. Nothing here interprets HTML or Markdown; the UI must render it as text.
 */
public final class Note {

    public static final int MAX_LENGTH = 140;

    private Note() {}

    /** The text to store, or {@code null} when the note is blank (which clears it). */
    public static String clean(String raw) {
        if (raw == null) {
            return null;
        }
        String text = raw.strip();
        if (text.isEmpty()) {
            return null;
        }
        if (text.codePointCount(0, text.length()) > MAX_LENGTH) {
            throw new CollectionException(Problem.NOTE_TOO_LONG);
        }
        if (text.codePoints().anyMatch(Character::isISOControl)) {
            throw new CollectionException(Problem.NOTE_INVALID);
        }
        return text;
    }
}
