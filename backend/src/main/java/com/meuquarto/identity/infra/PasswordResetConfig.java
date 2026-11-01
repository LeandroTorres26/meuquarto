package com.meuquarto.identity.infra;

import com.meuquarto.identity.domain.SiteUrl;
import java.security.SecureRandom;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Beans for the password reset use case (ADR 0012). */
@Configuration
public class PasswordResetConfig {

    /** {@code SITE_URL}; an invalid value stops the application at startup. */
    @Bean
    SiteUrl siteUrl(@Value("${meuquarto.site-url:}") String configured) {
        return SiteUrl.parse(configured);
    }

    @Bean
    SecureRandom secureRandom() {
        return new SecureRandom();
    }
}
