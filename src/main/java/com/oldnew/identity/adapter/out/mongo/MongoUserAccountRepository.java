package com.oldnew.identity.adapter.out.mongo;

import com.oldnew.identity.domain.error.DomainException;
import com.oldnew.identity.domain.error.ErrorCode;
import com.oldnew.identity.domain.model.EmailAddress;
import com.oldnew.identity.domain.model.PasswordHash;
import com.oldnew.identity.domain.model.Role;
import com.oldnew.identity.domain.model.UserAccount;
import com.oldnew.identity.domain.model.UserId;
import com.oldnew.identity.domain.port.UserAccountRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/** Adapter: stores {@link UserAccount} aggregates in the "users" collection. */
@Repository
class MongoUserAccountRepository implements UserAccountRepository {

    private final SpringDataUserRepository repository;

    MongoUserAccountRepository(SpringDataUserRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<UserAccount> findById(UserId id) {
        return repository.findById(id.value()).map(MongoUserAccountRepository::toDomain);
    }

    @Override
    public Optional<UserAccount> findByEmail(EmailAddress email) {
        return repository.findByEmail(email.value()).map(MongoUserAccountRepository::toDomain);
    }

    @Override
    public boolean existsByEmail(EmailAddress email) {
        return repository.existsByEmail(email.value());
    }

    @Override
    public UserAccount save(UserAccount account) {
        try {
            return toDomain(repository.save(toDocument(account)));
        } catch (DuplicateKeyException e) {
            throw new DomainException(ErrorCode.EMAIL_ALREADY_REGISTERED, "An account with this e-mail already exists", e);
        } catch (OptimisticLockingFailureException e) {
            throw new DomainException(ErrorCode.CONCURRENT_UPDATE, "The account was changed by another request; retry", e);
        }
    }

    private static UserDocument toDocument(UserAccount a) {
        Set<String> roles = new TreeSet<>();
        a.roles().forEach(r -> roles.add(r.name()));
        return new UserDocument(a.id().value(), a.email().value(), a.passwordHash().value(), roles,
                a.failedLoginAttempts(), a.lockedUntil(), a.createdAt(), a.version());
    }

    private static UserAccount toDomain(UserDocument d) {
        Set<Role> roles = EnumSet.noneOf(Role.class);
        if (d.roles() != null) {
            d.roles().forEach(r -> roles.add(Role.valueOf(r)));
        }
        return UserAccount.reconstitute(new UserId(d.id()), EmailAddress.of(d.email()), new PasswordHash(d.passwordHash()),
                roles, d.failedLoginAttempts(), d.lockedUntil(), d.createdAt(), d.version());
    }
}
