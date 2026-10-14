package com.oldnew.identity.config;

import com.oldnew.identity.domain.model.LockoutPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
class CoreConfig {

    /** One clock for the whole app, so tests can fix time. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    LockoutPolicy lockoutPolicy(IdentityProperties properties) {
        return new LockoutPolicy(properties.lockout().maxFailedAttempts(), properties.lockout().lockDuration());
    }
}
