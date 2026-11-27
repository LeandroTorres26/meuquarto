package com.meuquarto.shared.storage;

import static org.assertj.core.api.Assertions.assertThat;

import com.meuquarto.support.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/** The PostgreSQL-backed storage: put, replace, get and delete against a real database. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class DatabaseObjectStorageTests {

    @Autowired
    ObjectStorage storage;

    @Test
    void storesReadsReplacesAndDeletes() {
        String key = "avatars/storage-test.jpg";
        assertThat(storage.get(key)).isEmpty();

        storage.put(key, new byte[] {1, 2, 3}, "image/jpeg");
        ObjectStorage.StoredObject first = storage.get(key).orElseThrow();
        assertThat(first.content()).containsExactly(1, 2, 3);
        assertThat(first.contentType()).isEqualTo("image/jpeg");

        storage.put(key, new byte[] {9}, "image/png");
        ObjectStorage.StoredObject second = storage.get(key).orElseThrow();
        assertThat(second.content()).containsExactly(9);
        assertThat(second.contentType()).isEqualTo("image/png");

        storage.delete(key);
        assertThat(storage.get(key)).isEmpty();
        storage.delete(key); // deleting what is not there is not an error
    }
}
