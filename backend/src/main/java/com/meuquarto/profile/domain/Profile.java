package com.meuquarto.profile.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Optional;

/** One profile per user, reachable at its slug, with the name, bio, avatar and theme the owner edits (FR-05). */
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

    @Column(name = "display_name", length = DisplayName.MAX_LENGTH)
    private String displayName;

    @Column(length = Bio.MAX_LENGTH)
    private String bio;

    /** Object key of the avatar in storage, never a URL or a file name. */
    @Column(name = "avatar_key", length = 80)
    private String avatarKey;

    @Column(name = "theme_id", nullable = false, length = 30)
    private String themeId = ProfileTheme.DEFAULT.id();

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

    /** The name the owner chose, or empty while the slug stands in for it. */
    public Optional<String> displayName() {
        return Optional.ofNullable(displayName);
    }

    public Optional<String> bio() {
        return Optional.ofNullable(bio);
    }

    public Optional<String> avatarKey() {
        return Optional.ofNullable(avatarKey);
    }

    public ProfileTheme theme() {
        return ProfileTheme.find(themeId).orElse(ProfileTheme.DEFAULT);
    }

    public Instant createdAt() {
        return createdAt;
    }

    public void rename(DisplayName name) {
        this.displayName = name.value();
    }

    /** An empty bio clears it. */
    public void changeBio(Bio newBio) {
        this.bio = newBio.isEmpty() ? null : newBio.value();
    }

    public void changeSlug(Slug newSlug) {
        this.slug = newSlug.value();
    }

    public void changeTheme(ProfileTheme newTheme) {
        this.themeId = newTheme.id();
    }

    public void useAvatar(String key) {
        this.avatarKey = key;
    }

    public void removeAvatar() {
        this.avatarKey = null;
    }
}
