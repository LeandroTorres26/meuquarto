package com.meuquarto.shared.ratelimit;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitConfig {

    @Bean
    FixedWindowLimiter rateLimiter() {
        return new FixedWindowLimiter(Clock.systemUTC());
    }
}
