package com.meuquarto.profile.infra;

import com.meuquarto.shared.domain.MediaCategory;

/** A visible (not hidden) collection item id with its media item and category; light projection for public reads. */
public record VisibleRef(long itemId, long mediaItemId, MediaCategory category) {}
