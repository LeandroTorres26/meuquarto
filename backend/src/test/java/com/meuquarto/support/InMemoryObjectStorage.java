package com.meuquarto.support;

import com.meuquarto.shared.storage.ObjectStorage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** A storage that keeps objects in a map and remembers every call, for unit tests that must not need a server. */
public class InMemoryObjectStorage implements ObjectStorage {

    private final Map<String, StoredObject> objects = new ConcurrentHashMap<>();
    private final List<String> calls = new ArrayList<>();

    @Override
    public synchronized void put(String key, byte[] content, String contentType) {
        calls.add("put " + key);
        objects.put(key, new StoredObject(content, contentType));
    }

    @Override
    public synchronized Optional<StoredObject> get(String key) {
        calls.add("get " + key);
        return Optional.ofNullable(objects.get(key));
    }

    @Override
    public synchronized void delete(String key) {
        calls.add("delete " + key);
        objects.remove(key);
    }

    public synchronized List<String> calls() {
        return List.copyOf(calls);
    }

    public boolean contains(String key) {
        return objects.containsKey(key);
    }
}
