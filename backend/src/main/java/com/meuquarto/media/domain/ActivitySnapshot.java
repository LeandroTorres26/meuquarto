package com.meuquarto.media.domain;

import java.util.List;

/** What a provider returned for one user in one fetch: an immutable list of entries. */
public record ActivitySnapshot(List<ActivityEntry> entries) {

    public ActivitySnapshot {
        entries = List.copyOf(entries);
    }
}
