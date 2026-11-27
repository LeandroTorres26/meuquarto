package com.meuquarto.shared.storage;

import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * {@link ObjectStorage} that keeps each object as a row of the {@code stored_objects} table. Avatars are
 * small (re-encoded and limited in size), so a bytea column is enough.
 */
@Component
class DatabaseObjectStorage implements ObjectStorage {

    private final JdbcTemplate jdbc;

    DatabaseObjectStorage(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void put(String key, byte[] content, String contentType) {
        // Insert, or replace the row when the key already exists.
        jdbc.update("""
                INSERT INTO stored_objects (key, content, content_type) VALUES (?, ?, ?)
                ON CONFLICT (key) DO UPDATE SET content = EXCLUDED.content, content_type = EXCLUDED.content_type
                """, key, content, contentType);
    }

    @Override
    public Optional<StoredObject> get(String key) {
        return jdbc.query("SELECT content, content_type FROM stored_objects WHERE key = ?",
                (rs, row) -> new StoredObject(rs.getBytes("content"), rs.getString("content_type")), key)
                .stream().findFirst();
    }

    @Override
    public void delete(String key) {
        jdbc.update("DELETE FROM stored_objects WHERE key = ?", key);
    }
}
