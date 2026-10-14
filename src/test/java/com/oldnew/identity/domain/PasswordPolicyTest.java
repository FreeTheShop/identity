package com.oldnew.identity.domain;

import com.oldnew.identity.domain.error.DomainException;
import com.oldnew.identity.domain.error.ErrorCode;
import com.oldnew.identity.domain.model.EmailAddress;
import com.oldnew.identity.domain.model.PasswordPolicy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordPolicyTest {

    private static final EmailAddress EMAIL = EmailAddress.of("alice@example.com");

    @Test
    void acceptsTwelveCharacters() {
        assertThatCode(() -> PasswordPolicy.check("twelve-chars", EMAIL)).doesNotThrowAnyException();
    }

    @Test
    void rejectsElevenCharacters() {
        assertThatThrownBy(() -> PasswordPolicy.check("eleven-char", EMAIL))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).code()).isEqualTo(ErrorCode.WEAK_PASSWORD);
    }

    @Test
    void rejectsOverlongPasswords() {
        assertThatThrownBy(() -> PasswordPolicy.check("x".repeat(129), EMAIL)).isInstanceOf(DomainException.class);
    }

    @Test
    void rejectsTheEmailItself() {
        assertThatThrownBy(() -> PasswordPolicy.check("ALICE@example.com", EMAIL)).isInstanceOf(DomainException.class);
    }
}
