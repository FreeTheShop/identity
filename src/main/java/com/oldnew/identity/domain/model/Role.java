package com.oldnew.identity.domain.model;

/** Coarse roles. Fine-grained rules live in each service's use cases. */
public enum Role {
    /** A shopper. Only self-signed-up accounts on the Customer Identity have it. */
    CUSTOMER,
    CATALOG_MANAGER,
    ADMIN,
    /** May create staff accounts on the Staff Identity (ADR-0009). Separate from ADMIN on purpose. */
    ACCOUNT_CREATOR
}
