package com.meuquarto.shared.storage;

import java.util.Optional;

/**
 * A private place for small files (avatars). The rest of the code talks to this interface, so where the
 * bytes live can change and tests can use an in-memory copy. Today they live in PostgreSQL
 * ({@link DatabaseObjectStorage}).
 * <p>
 * Keys are chosen by the caller and must never come from a user. Nothing here is public: objects
 * reach the browser only through an endpoint of ours.
 */
public interface ObjectStorage {

    /** Stores (or replaces) the object. */
    void put(String key, byte[] content, String contentType);

    /** The object, or empty when there is none under this key. */
    Optional<StoredObject> get(String key);

    /** Removes the object; removing one that is not there is not an error. */
    void delete(String key);

    /** An object read back whole. Only small objects (avatars) are stored, so holding the bytes is fine. */
    record StoredObject(byte[] content, String contentType) {}
}
