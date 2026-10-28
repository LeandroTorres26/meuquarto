package com.meuquarto.media.infra;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Turns on {@code @Scheduled} processing (ADR 0007). On by default; the tests switch it off
 * ({@code meuquarto.sync.scheduler.enabled=false}) so nothing runs behind their back.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "meuquarto.sync.scheduler.enabled", havingValue = "true", matchIfMissing = true)
class SyncSchedulingConfig {
}
