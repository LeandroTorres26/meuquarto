package com.meuquarto.profile.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** One profile per user, reachable at its slug. Name, bio and avatar come with the profile editor (#15). */
@Entity
@Table(name = "profiles")
public class Profile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true, updatable = false)
    private long userId;

    @Column(nullable = false, length = Slug.MAX_LENGTH)
    private String slug;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Profile() {
        // for JPA
    }

    public Profile(long userId, Slug slug) {
        this.userId = userId;
        this.slug = slug.value();
        this.createdAt = Instant.now();
    }

    public Long id() {
        return id;
    }

    public long userId() {
        return userId;
    }

    public String slug() {
        return slug;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
