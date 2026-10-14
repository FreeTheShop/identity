package com.oldnew.identity.domain;

import com.oldnew.identity.domain.error.DomainException;
import com.oldnew.identity.domain.error.ErrorCode;
import com.oldnew.identity.domain.model.EmailAddress;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmailAddressTest {

    @Test
    void normalisesCaseAndSpaces() {
        assertThat(EmailAddress.of("  Alice@Example.COM ").value()).isEqualTo("alice@example.com");
    }

    @Test
    void masksForLogs() {
        assertThat(EmailAddress.of("alice@example.com").toString()).isEqualTo("a***@example.com");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "alice", "alice@", "@example.com", "alice@example", "a b@example.com"})
    void rejectsMalformed(String raw) {
        assertThatThrownBy(() -> EmailAddress.of(raw))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.INVALID_EMAIL));
    }

    @Test
    void rejectsTooLong() {
        String raw = "a".repeat(60) + "@" + "b".repeat(200) + ".com";
        assertThatThrownBy(() -> EmailAddress.of(raw)).isInstanceOf(DomainException.class);
    }
}
