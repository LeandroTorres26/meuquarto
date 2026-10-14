package com.meuquarto;

import static org.assertj.core.api.Assertions.assertThat;

import com.meuquarto.support.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class DatabaseBaselineTests {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void migrationsRunOnACleanDatabase() {
        Integer failed = jdbc.queryForObject(
                "select count(*) from flyway_schema_history where success = false", Integer.class);
        Integer applied = jdbc.queryForObject(
                "select count(*) from flyway_schema_history where success = true", Integer.class);

        assertThat(failed).isZero();
        assertThat(applied).isPositive();
    }

    @Test
    void runsOnTheSamePostgresMajorVersionAsCompose() {
        String version = jdbc.queryForObject("show server_version", String.class);

        assertThat(version).startsWith("17.");
    }
}
