package com.meuquarto;

import com.meuquarto.support.TestcontainersConfiguration;
import org.springframework.boot.SpringApplication;

/** Runs the API locally against a throwaway PostgreSQL container: {@code ./mvnw spring-boot:test-run}. */
public class TestApiApplication {

    public static void main(String[] args) {
        SpringApplication.from(ApiApplication::main).with(TestcontainersConfiguration.class).run(args);
    }
}
