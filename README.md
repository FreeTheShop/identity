# Identity Service

Issues and checks tokens for the Old New Ecosystem. Built on Spring Authorization Server (OAuth2 + OIDC) with Spring Boot 4.1, Java 21 and MongoDB.

One image runs as **two instances** (ADR-0009 in `platform/docs/adr`), each with its own issuer, database, signing
keys and session cookie. Exactly one of the profiles `customer` or `staff` picks the instance; with neither or both,
Identity refuses to start.

| | Customer Identity (`customer`) | Staff Identity (`staff`) |
| --- | --- | --- |
| Port | 9000 | 9001 |
| Used by | Client UI (shoppers) | Admin UI (staff), and the services' own tokens |
| Accounts | Self sign-up, CUSTOMER only | No sign-up. An ACCOUNT_CREATOR creates staff accounts; the first admin comes from the bootstrap secret |
| Session cookie | `OLDNEW_CUSTOMER_SESSION` | `OLDNEW_STAFF_SESSION` |

- **Port 9000 or 9001:** OAuth2/OIDC endpoints, login page, `/api/v1/**`
- **Port 8081:** health, metrics. Internal only; never expose it publicly.

## Quick start (local, about 5 minutes)

Prerequisites: JDK 21, Maven 3.9+, Docker.

```bash
brew install openjdk@21 maven      # macOS, once
docker compose up -d               # MongoDB on :27017
mvn spring-boot:run -Dspring-boot.run.profiles=dev,customer
# the Staff Identity, in a second terminal: its own database and management port
MONGODB_URI=mongodb://localhost:27017/staff-identity mvn spring-boot:run -Dspring-boot.run.profiles=dev,staff \
  -Dspring-boot.run.arguments=--management.server.port=9181
```

The `dev` profile:
- generates a throw-away signing key
- turns on Swagger UI at `/swagger-ui.html`
- on the Staff Identity, creates `admin@oldnew.local` / `dev-only-admin-password` with ADMIN and ACCOUNT_CREATOR

Get a service token (Staff Identity):

```bash
curl -s -u price-service:dev-price-secret \
  -d grant_type=client_credentials -d scope=product:internal \
  http://localhost:9001/oauth2/token
```

To generate the Maven wrapper once and commit it: `mvn -N wrapper:wrapper -Dmaven=3.9.11`.

## Tests

| Command | Runs | Needs Docker |
| --- | --- | --- |
| `mvn test` | Domain unit tests, ArchUnit layering rules | No |
| `mvn verify` | Everything above, plus `*IT` integration tests and Cucumber features against real MongoDB | Yes |
| `mvn -Powasp verify` | OWASP dependency-check; fails on CVSS ≥ 7 (needs `NVD_API_KEY`) | No |

Work test-first:
1. Write the scenario in `src/test/resources/features/`.
2. Write a failing domain test.
3. Make it pass.
4. Add the slice or integration test.
5. Refactor.

## Endpoints

| Path | Who | Notes |
| --- | --- | --- |
| `POST /oauth2/token` | Clients | `client_credentials` (services) or `authorization_code` + PKCE (UIs). 10/min per IP + client |
| `GET /oauth2/jwks` | Anyone | Public keys (ES256). Other services cache this |
| `GET /.well-known/openid-configuration` | Anyone | Discovery |
| `GET /login` | Browsers | Sign-in form. 10 POSTs/min per IP + username; 5 failures lock the account for 15 min |
| `POST /api/v1/users` | Public | Customer Identity only. Sign-up. Always gets the CUSTOMER role. 10/min per IP |
| `GET /api/v1/users/me` | Any valid token | The caller's own account, looked up by the token's `sub` |
| `POST /api/v1/admin/users` | ACCOUNT_CREATOR | Staff Identity only. Creates a staff account (e-mail, password, role CATALOG_MANAGER). Audited. Interim until the Sprint 6 invite flow: the creator sets the first password |
| `GET /api/v1/admin/users/{id}` | ADMIN | Staff Identity only |
| `POST /api/v1/admin/users/{id}/roles` | ADMIN | Staff Identity only. Audited. Takes effect at the user's next token. The only way to give ADMIN or ACCOUNT_CREATOR |

Every error is `application/problem+json` with a stable `code` and a `traceId`. Codes are listed in `domain/error/ErrorCode.java`.

## Clients

Registered per instance in `application-customer.yml` and `application-staff.yml`
(`spring.security.oauth2.authorizationserver.client.*`); Mongo-backed in Sprint 3.

| Client | Instance | Grant | Scopes |
| --- | --- | --- | --- |
| `client-ui` | Customer | Authorization code + PKCE (public) | `openid email product:read client-aggregator:read client-core:read` |
| `admin-ui` | Staff | Authorization code + PKCE (public) | `openid email product:read product:write admin-aggregator:read admin-core:read` |
| `price-service` | Staff | Client credentials | `product:internal price:read` |
| `validation-service` | Staff | Client credentials | `product:internal validation:read` |

The service clients live on the Staff Identity: it has no public sign-up, so it is the smaller target.

A UI asks for its aggregator's scope plus the scopes of the services the aggregator relays the user's token to.
The `price:read` and `validation:read` scopes on the services' own clients exist for the Sprint 2 status checks;
the real callers of Price and Validation get them when those services are built (Sprints 5 and 6).

## Tokens

- Access tokens are JWTs signed **ES256**, valid **10 minutes**, with the active `kid` in the header.
- `sub` is the user id, never the e-mail. `roles` lists the user's roles.
- `aud` is derived from scopes: any `<prefix>:*` scope adds `<prefix>-service` (`product:read` adds `product-service`,
  `admin-core:read` adds `admin-core-service`). The mapping lives in `identity.token.audience-by-scope-prefix`;
  a new service needs a line there.
- ID tokens carry `email` when the app asks for the `email` scope (the UIs do, to show who is signed in).
  Access tokens never carry it.
- Public UI clients get no refresh token; they sign in again through PKCE. A BFF with refresh-token rotation is on the roadmap.

## Configuration and secrets

Every secret comes from an environment variable or a file in `/run/secrets` (Spring configtree). `prod` refuses to start without them.

| Setting | Source |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | env, `prod,customer` or `prod,staff` (exactly one instance) |
| `MONGODB_URI` | env / secret. Each instance has its own database |
| `IDENTITY_ISSUER` | env, e.g. `https://login.example.com` or `https://staff-login.example.com`. Defaults: `:9000` customer, `:9001` staff |
| `IDENTITY_JWT_KEYS_DIR` | Mounted directory of private JWK files (default `/run/secrets/jwt`) |
| `IDENTITY_JWT_ACTIVE_KEY_ID` | env |
| `PRICE_SERVICE_CLIENT_SECRET` | Staff Identity. secret, an `{argon2id}` hash (below) |
| `VALIDATION_SERVICE_CLIENT_SECRET` | Staff Identity. secret, an `{argon2id}` hash (below) |
| `ADMIN_UI_REDIRECT_URIS` / `CLIENT_UI_REDIRECT_URIS` | env, the UIs' `/callback` addresses |
| `ADMIN_UI_POST_LOGOUT_REDIRECT_URIS` / `CLIENT_UI_POST_LOGOUT_REDIRECT_URIS` | env, where the UIs return after signing out (their home pages, `http://localhost:3000/` and `:3001/` by default) |
| `identity.cors.allowed-origins` | The UI's origin for this instance. Empty by default; `dev` allows `http://localhost:3000` and `:3001` (`IDENTITY_CORS_ALLOWED_ORIGINS`) |
| `IDENTITY_BOOTSTRAP_ADMIN_EMAIL` / `_PASSWORD` | Staff Identity. secret, optional, first start only. Gets ADMIN and ACCOUNT_CREATOR |

**Signing key.** To generate one:

```bash
mkdir -p secrets/jwt && java scripts/GenerateJwk.java identity-2026-10 > secrets/jwt/identity-2026-10.json
```

The output is a secret: store it in your secret manager, never in Git. For rotation, see `docs/runbooks/key-rotation.md`.

**Client secrets.** Store only the hash. With the `argon2` CLI (`brew install argon2`):

```bash
SECRET=$(openssl rand -base64 32)
echo "secret for the client: $SECRET"
printf '%s' "$SECRET" | argon2 "$(openssl rand -base64 12)" -id -t 2 -k 19456 -p 1 -l 32 -e | sed 's/^/{argon2id}/'
```

`{noop}` plain secrets are accepted only when `identity.security.allow-plaintext-client-secrets=true`, which `prod` refuses.

## Production checklist

- [ ] `SPRING_PROFILES_ACTIVE=prod,customer` or `prod,staff` (the image defaults to `prod`, which alone refuses to start)
- [ ] Each instance on its own host, with its own database, signing key and secrets
- [ ] TLS terminated in front; `server.forward-headers-strategy=native` (set in prod) so rate limits see the real IP
- [ ] Port 8081 reachable only by the orchestrator and Prometheus
- [ ] Signing key mounted; `IDENTITY_JWT_ACTIVE_KEY_ID` set
- [ ] One instance only until Sprint 2. Authorizations and rate-limit buckets are in memory today.

## Known gaps (tracked in the sprint plan)

- Registered clients and OAuth2 authorizations live in memory (from `application.yml`). They move to MongoDB in Sprint 2, which is required before running two instances.
- Sign-up returns 409 for an existing e-mail, so a caller can test whether an account exists. Rate limiting reduces this but does not remove it. Close it when e-mail verification exists by always answering 202 and e-mailing the owner.
- Rate limits are per instance (in memory). Move to `bucket4j-redis` when there are two or more instances.

## Layout

```
domain/       UserAccount aggregate, value objects, ports, error codes. Plain Java (ArchUnit-enforced)
application/  Use cases: RegisterUser, CreateStaffAccount, GetUser, GrantRole, LoginAttempts
adapter/in/   web (REST + Problem Details), security (form-login bridge)
adapter/out/  mongo (persistence), crypto (Argon2id)
config/       Security chains, token shaping, keys, rate limits, OpenAPI
```

Architecture, decisions and the roadmap live in the Old New Ecosystem architecture and sprint docs. The ADRs that touch this service are in `docs/adr/`.
