package com.meuquarto.support;

import java.util.UUID;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * A real S3-compatible server for the tests that store files (ADR 0009, 0021), same image as the local
 * Docker Compose stack. Import it next to {@link TestcontainersConfiguration}. The bucket does not need to
 * exist: the API creates it on first use, exactly as it does against the compose service.
 */
@TestConfiguration(proxyBeanMethods = false)
public class StorageTestcontainersConfiguration {

    public static final String STORAGE_IMAGE = "rustfs/rustfs:1.0.3";
    // Random per test run: the container lives only for this JVM, and no credential sits in the source.
    private static final String ACCESS_KEY = "test-" + UUID.randomUUID();
    private static final String SECRET_KEY = UUID.randomUUID().toString();

    @Bean
    GenericContainer<?> storageContainer() {
        return new GenericContainer<>(DockerImageName.parse(STORAGE_IMAGE))
                .withExposedPorts(9000)
                .withEnv("RUSTFS_ACCESS_KEY", ACCESS_KEY)
                .withEnv("RUSTFS_SECRET_KEY", SECRET_KEY)
                .waitingFor(Wait.forHttp("/health").forPort(9000));
    }

    @Bean
    DynamicPropertyRegistrar storageProperties(GenericContainer<?> storageContainer) {
        return registry -> {
            registry.add("meuquarto.storage.endpoint",
                    () -> "http://" + storageContainer.getHost() + ":" + storageContainer.getMappedPort(9000));
            registry.add("meuquarto.storage.access-key", () -> ACCESS_KEY);
            registry.add("meuquarto.storage.secret-key", () -> SECRET_KEY);
            registry.add("meuquarto.storage.bucket", () -> "meuquarto-test");
        };
    }
}
