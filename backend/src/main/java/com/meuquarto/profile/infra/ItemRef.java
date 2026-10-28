package com.meuquarto.profile.infra;

/** A collection item id with the media item it points to; light projection for sorted listings. */
public record ItemRef(long itemId, long mediaItemId) {}
