# ADR-0001: Identity is a Spring Authorization Server

**Status:** Proposed · **Date:** 2026-10-08 · **Decider:** Harshit

## Context
Every service authenticates through Identity. Issuing tokens, publishing keys, rotating refresh tokens and revoking access are easy to get subtly wrong.

## Options
| Option | Complexity | Ops cost | Fit |
| --- | --- | --- | --- |
| **Spring Authorization Server (chosen)** | Medium | Inside this app | Same stack; standard OAuth2/OIDC endpoints |
| Hand-written JWT issuer | Low now, high later | Low | Every security bug is ours |
| Keycloak | Low code, high config | A second server to run and patch | Too much for one developer |

## Decision
Use Spring Authorization Server, which is part of Spring Security 7.1. Access tokens are ES256 JWTs valid for 10 minutes. `sub` is the user id.

## Consequences
- `/oauth2/token`, `/oauth2/jwks`, discovery and revocation come built in.
- Clients and authorizations must move to MongoDB (Sprint 2) before running more than one instance.
