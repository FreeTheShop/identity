# ADR-0002: Services validate JWTs locally against JWKS

**Status:** Proposed · **Date:** 2026-10-08

## Decision
Every resource server caches Identity's public keys from `/oauth2/jwks`. It checks signature, `exp`, `iss` and `aud` itself, and never calls Identity on each request.

## Consequences
- Identity being briefly down does not stop other services, and no request pays an extra hop.
- A revoked user keeps access until their token expires, at most 10 minutes. High-risk actions can use introspection later.
