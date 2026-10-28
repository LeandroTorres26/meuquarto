package com.meuquarto.media.domain;

import com.meuquarto.shared.domain.MediaCategory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Snapshot of a work's metadata (ADR 0006), identified by its {@link MediaKey} (BR-04). Shared by all
 * users; profiles read this row and never call the provider. Rows are written by the snapshot
 * service; this class only reads.
 */
@Entity
@Table(name = "media_items")
public class MediaItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private MediaCategory category;

    @Column(name = "canonical_provider", nullable = false, updatable = false)
    private String canonicalProvider;

    @Column(name = "external_id", nullable = false, updatable = false)
    private String externalId;

    @Column(nullable = false)
    private String title;

    private Integer year;

    @Column(name = "image_url")
    private String imageUrl;

    @Column(nullable = false)
    private boolean adult;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    protected MediaItem() {
        // for JPA
    }

    public Long id() {
        return id;
    }

    public MediaKey key() {
        return new MediaKey(category, canonicalProvider, externalId);
    }

    public String title() {
        return title;
    }

    public Integer year() {
        return year;
    }

    public String imageUrl() {
        return imageUrl;
    }

    public boolean adult() {
        return adult;
    }

    public Instant fetchedAt() {
        return fetchedAt;
    }
}
