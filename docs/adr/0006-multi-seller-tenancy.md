# ADR-0006: Several sellers, so the platform is multi-tenant (built in Sprint 2)

**Status:** Accepted (design) · **Date:** 2026-10-08 · **Decider:** Harshit

## Context
- Several sellers sell through the platform.
- Shoppers see a marketplace across all sellers, plus one storefront page per seller.
- Seller staff use the Premium view. An admin creates their accounts; consumers sign up themselves.

## Decision
- **Identity**
  - Owns a `Seller` aggregate: id, name, slug, status ACTIVE/SUSPENDED.
  - Owns staff membership: user to seller, role SELLER_ADMIN or CATALOG_MANAGER. One seller per staff account to start.
  - Staff tokens carry a `seller_id` claim. Consumer and platform-admin tokens do not.
- **Seller-owned records** carry an immutable `sellerId`.
  - The seller always comes from the token, never from the request body or path.
  - Repository methods for seller data require a `SellerId` argument; there is no unscoped finder.
  - Another seller's record answers 404, not 403, so staff cannot probe which ids exist.
- **Platform admins** reach seller data only through `/api/v1/admin/sellers/{sellerId}/...`. Every call is audited with actor and seller.
- **Premium accounts** are created by invite:
  - The admin names an e-mail and a role. Identity stores a hashed one-time token that expires after 72 hours.
  - The person sets their own password from the e-mailed link. No admin ever sets or sees a password.
- **Suspending a seller** hides its products from the shop at once. Staff lose access at their next token (at most 10 minutes).

## Consequences
- Product: unique index becomes `{sellerId, sku}`; catalog indexes gain `sellerId`; `/api/v1/admin/products` becomes `/api/v1/seller/products` (unreleased API, so no compatibility cost).
- Identity needs an e-mail provider for invites. Still open.
- Price and Validation follow the same rule when built.
