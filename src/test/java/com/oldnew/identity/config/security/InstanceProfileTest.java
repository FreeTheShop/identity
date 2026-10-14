package com.oldnew.identity.config.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** ADR-0009: an Identity is the Customer or the Staff Identity, never neither and never both. */
class InstanceProfileTest {

    @Test
    void startsAsExactlyOneInstance() {
        assertThatCode(() -> new SecurityConfig(profiles("prod", "customer"))).doesNotThrowAnyException();
        assertThatCode(() -> new SecurityConfig(profiles("prod", "staff"))).doesNotThrowAnyException();
    }

    @Test
    void refusesToStartAsNeitherOrBoth() {
        assertThatThrownBy(() -> new SecurityConfig(profiles("prod")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("exactly one");
        assertThatThrownBy(() -> new SecurityConfig(profiles("customer", "staff")))
                .isInstanceOf(IllegalStateException.class);
    }

    private static MockEnvironment profiles(String... names) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(names);
        return environment;
    }
}
