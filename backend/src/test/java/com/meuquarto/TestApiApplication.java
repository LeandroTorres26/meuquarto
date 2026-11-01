package com.meuquarto;

import com.meuquarto.support.StorageTestcontainersConfiguration;
import com.meuquarto.support.TestcontainersConfiguration;
import org.springframework.boot.SpringApplication;

/**
 * Runs the API locally against a throwaway PostgreSQL and a throwaway S3-compatible server (for
 * avatars): {@code ./mvnw spring-boot:test-run}.
 */
public class TestApiApplication {

    public static void main(String[] args) {
        SpringApplication.from(ApiApplication::main)
                .with(TestcontainersConfiguration.class, StorageTestcontainersConfiguration.class).run(args);
    }
}
