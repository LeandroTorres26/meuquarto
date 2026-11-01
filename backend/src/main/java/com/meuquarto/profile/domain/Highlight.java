package com.meuquarto.profile.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** One collection item marked "Destacar" in a list, at a position (0 is first). Changed only by its {@link HighlightList}. */
@Entity
@Table(name = "highlights")
public class Highlight {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "collection_item_id", nullable = false, updatable = false)
    private long collectionItemId;

    @Column(nullable = false)
    private int position;

    protected Highlight() {
        // for JPA
    }

    Highlight(long collectionItemId, int position) {
        this.collectionItemId = collectionItemId;
        this.position = position;
    }

    void moveTo(int newPosition) {
        this.position = newPosition;
    }

    public Long id() {
        return id;
    }

    public long collectionItemId() {
        return collectionItemId;
    }

    public int position() {
        return position;
    }
}
