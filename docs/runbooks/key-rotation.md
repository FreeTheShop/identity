# Runbook: rotate the token signing key

**When:** every 90 days, or immediately if a key may have leaked.
**Access needed:** your secret store and the deploy pipeline.
**Time:** about 30 minutes of waiting, 5 minutes of work.

Why there is a wait: other services cache the JWKS, and tokens live 10 minutes. A key must be published before it signs anything, and kept until every token it signed has expired.

1. **Generate the next key:** `mkdir -p secrets/jwt && java scripts/GenerateJwk.java identity-YYYY-MM > secrets/jwt/identity-YYYY-MM.json` (the `secrets/` folder is git-ignored). Add it to the secret mounted at `IDENTITY_JWT_KEYS_DIR`, next to the current key.
2. **Deploy without changing `IDENTITY_JWT_ACTIVE_KEY_ID`.** Check that `curl https://<identity>/oauth2/jwks` lists two `kid`s.
3. **Wait 15 minutes.** That covers the JWKS caches in other services.
4. **Activate:** set `IDENTITY_JWT_ACTIVE_KEY_ID=identity-YYYY-MM` and deploy. Decode a fresh token and check the header `kid`.
5. **Wait 15 minutes.** That is one full token lifetime plus margin.
6. **Remove the old key file** from the secret and deploy. JWKS now lists one `kid`.

**Leaked key (emergency):**
- Do steps 1, 2 and 4 back to back.
- Then remove the leaked key at once (step 6) instead of waiting.
- Users and services holding tokens signed by it get 401 and must sign in again. Accept that.

**Rollback:** set `IDENTITY_JWT_ACTIVE_KEY_ID` back to the old `kid`. This works only while the old key is still in the directory.
