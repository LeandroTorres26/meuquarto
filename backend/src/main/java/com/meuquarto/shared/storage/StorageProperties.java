package com.meuquarto.shared.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Where the S3-compatible bucket is ({@code meuquarto.storage.*}, filled from the {@code STORAGE_*}
 * environment variables). The keys are secrets: they come from the environment only.
 */
@ConfigurationProperties("meuquarto.storage")
public record StorageProperties(
        @DefaultValue("http://localhost:9000") String endpoint,
        @DefaultValue("us-east-1") String region,
        @DefaultValue("meuquarto") String bucket,
        @DefaultValue("") String accessKey,
        @DefaultValue("") String secretKey) {}
