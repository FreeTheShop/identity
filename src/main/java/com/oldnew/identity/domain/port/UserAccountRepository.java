package com.oldnew.identity.domain.port;

import com.oldnew.identity.domain.model.EmailAddress;
import com.oldnew.identity.domain.model.UserAccount;
import com.oldnew.identity.domain.model.UserId;

import java.util.Optional;

/**
 * Port the domain owns; a persistence adapter implements it.
 * {@link #save} throws {@code DomainException(EMAIL_ALREADY_REGISTERED)} on a duplicate e-mail and
 * {@code DomainException(CONCURRENT_UPDATE)} when the stored version moved on.
 */
public interface UserAccountRepository {

    Optional<UserAccount> findById(UserId id);

    Optional<UserAccount> findByEmail(EmailAddress email);

    boolean existsByEmail(EmailAddress email);

    UserAccount save(UserAccount account);
}
