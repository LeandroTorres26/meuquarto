package com.meuquarto.shared.storage;

import java.util.Optional;

/**
 * A private bucket of immutable objects (ADR 0009). The rest of the code talks to this interface, so
 * switching from R2 to a local server is configuration and tests can use an in-memory copy.
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
