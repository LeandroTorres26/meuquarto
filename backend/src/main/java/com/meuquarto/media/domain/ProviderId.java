package com.meuquarto.media.domain;

/**
 * The platforms a user can link by username (ADR 0004). The name is stored in
 * {@code linked_accounts.provider} and in {@code collection_item_sources.source}, so it never changes.
 */
public enum ProviderId {
    LETTERBOXD, ANILIST, STEAM, LASTFM, GOODREADS
}
