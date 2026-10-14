#!/usr/bin/env bash
# Get a USER access token (authorization code + PKCE) from a locally running Identity, for trying the APIs.
#   ./scripts/get-user-token.sh                 # admin-ui client: scopes openid product:read product:write
#   CLIENT_ID=client-ui SCOPES="openid product:read" REDIRECT_URI=http://localhost:3001/callback ./scripts/get-user-token.sh
# Dev only. Prints the access token on the last line, so you can do: TOKEN=$(./scripts/get-user-token.sh | tail -1)
set -euo pipefail

ISSUER="${ISSUER:-http://localhost:9000}"
CLIENT_ID="${CLIENT_ID:-admin-ui}"
REDIRECT_URI="${REDIRECT_URI:-http://localhost:3000/callback}"
SCOPES="${SCOPES:-openid product:read product:write}"

b64url() { openssl base64 -A | tr '+/' '-_' | tr -d '='; }
VERIFIER=$(openssl rand 48 | b64url)
CHALLENGE=$(printf '%s' "$VERIFIER" | openssl dgst -sha256 -binary | b64url)
STATE=$(openssl rand 16 | b64url)
SCOPE_Q=$(printf '%s' "$SCOPES" | sed 's/ /%20/g')

URL="$ISSUER/oauth2/authorize?response_type=code&client_id=$CLIENT_ID&redirect_uri=$REDIRECT_URI&scope=$SCOPE_Q&code_challenge=$CHALLENGE&code_challenge_method=S256&state=$STATE"

echo "1) Sign in in the browser window that opens (dev admin: admin@oldnew.local / dev-only-admin-password)." >&2
echo "2) You will land on $REDIRECT_URI?code=...  The page itself will not load; that's expected." >&2
echo "3) Copy the WHOLE address from the browser bar and paste it here." >&2
open "$URL" 2>/dev/null || echo "Open this URL: $URL" >&2
read -r -p "Redirected URL: " REDIRECTED

CODE=$(printf '%s' "$REDIRECTED" | sed -n 's/.*[?&]code=\([^&]*\).*/\1/p')
GOT_STATE=$(printf '%s' "$REDIRECTED" | sed -n 's/.*[?&]state=\([^&]*\).*/\1/p')
[ -n "$CODE" ] || { echo "No code found in that URL." >&2; exit 1; }
[ "$GOT_STATE" = "$STATE" ] || { echo "State mismatch: not the login you started. Try again." >&2; exit 1; }

RESPONSE=$(curl -s -X POST "$ISSUER/oauth2/token" \
  -d grant_type=authorization_code -d client_id="$CLIENT_ID" -d code="$CODE" \
  -d redirect_uri="$REDIRECT_URI" -d code_verifier="$VERIFIER")
TOKEN=$(printf '%s' "$RESPONSE" | sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p')
[ -n "$TOKEN" ] || { echo "Token request failed: $RESPONSE" >&2; exit 1; }
echo "Access token (valid 10 minutes):" >&2
echo "$TOKEN"
