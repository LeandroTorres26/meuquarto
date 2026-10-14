package com.meuquarto.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Real PostgreSQL for integration tests, same version as infra/docker-compose.yml.
 * Import it with {@code @Import(TestcontainersConfiguration.class)}; Spring reuses one container per test context.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    public static final String POSTGRES_IMAGE = "postgres:17.11-alpine3.24";

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(DockerImageName.parse(POSTGRES_IMAGE).asCompatibleSubstituteFor("postgres"));
    }
}
