package com.oldnew.identity.application;

import com.oldnew.identity.domain.error.DomainException;
import com.oldnew.identity.domain.error.ErrorCode;
import com.oldnew.identity.domain.model.UserId;
import com.oldnew.identity.domain.port.UserAccountRepository;
import org.springframework.stereotype.Service;

/** Query: read one account. Callers decide who may ask (self or admin). */
@Service
public class GetUser {

    private final UserAccountRepository users;

    public GetUser(UserAccountRepository users) {
        this.users = users;
    }

    public UserView handle(UserId id) {
        return users.findById(id)
                .map(UserView::of)
                .orElseThrow(() -> new DomainException(ErrorCode.USER_NOT_FOUND, "User not found"));
    }
}
