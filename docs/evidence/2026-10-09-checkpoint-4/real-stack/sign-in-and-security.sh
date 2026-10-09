#!/bin/bash
# Checkpoint 4 real-stack check: browser-like sign-in through the frontend on
# :3000 (Next.js forwards /api, /oauth2, /login and /logout to the backend),
# the mock identity provider on :8180, the backend with the demo profile.
# Uses curl cookie jars as browsers. Needs curl and jq.
F=http://localhost:3000
CFG=0d3d0000-0000-4000-8000-000000000002
A=$(mktemp); B=$(mktemp); O=$(mktemp)
trap 'rm -f $A $B $O' EXIT

xsrf() { awk '$6 == "XSRF-TOKEN" { print $7 }' "$1"; }
session() { awk '$6 == "JSESSIONID" { print substr($7, 1, 8) "…" }' "$1"; }
# Sign in as $2 using the provider's login form, the way a browser would.
login() {
  local auth callback
  auth=$(curl -s -c "$1" -b "$1" -o /dev/null -w '%{redirect_url}' $F/oauth2/authorization/oidc)
  callback=$(curl -s -o /dev/null -w '%{redirect_url}' -X POST "$auth" --data-urlencode "username=$2" --data-urlencode "claims=")
  curl -s -c "$1" -b "$1" -o /dev/null -w "sign-in as $2: callback HTTP %{http_code}, redirected to %{redirect_url}\n" "$callback"
}
say() { echo; echo "## $*"; }

say "1. Sign in as two customers and an operator (mock identity provider)"
login $A customer-a; login $B customer-b; login $O operator-1
echo "session cookies (HttpOnly) for A: $(session $A); XSRF-TOKEN present for A: $([ -n "$(xsrf $A)" ] && echo yes)"
echo "A /api/me: $(curl -s -b $A $F/api/me | jq -c '{email, roles}')"
echo "O /api/me: $(curl -s -b $O $F/api/me | jq -c '{email, roles}')"

say "2. A places an order: with the CSRF header, then without it"
ORDER=$(curl -s -b $A -c $A -X POST $F/api/orders -H 'Content-Type: application/json' -H "X-XSRF-TOKEN: $(xsrf $A)" \
  -d "{\"productConfigurationId\":\"$CFG\",\"quantity\":1}")
echo "with header: $(echo $ORDER | jq -c '{status, total}')"
ID=$(echo $ORDER | jq -r .id)
echo "without header: $(curl -s -b $A -X POST $F/api/orders -H 'Content-Type: application/json' -d "{\"productConfigurationId\":\"$CFG\"}" -w ' HTTP %{http_code}' | sed 's/.*"code":"\([A-Z_]*\)".*HTTP/\1 HTTP/')"

say "3. Ownership: A reads the order; B cannot read it or pay for it"
echo "A GET order: HTTP $(curl -s -o /dev/null -w '%{http_code}' -b $A $F/api/orders/$ID)"
echo "B GET order: $(curl -s -b $B $F/api/orders/$ID -w ' HTTP %{http_code}' | sed 's/.*"code":"\([A-Z_]*\)".*HTTP/\1 HTTP/')"
echo "B pays A's order: $(curl -s -b $B -X POST $F/api/demo/orders/$ID/simulate-payment -H "X-XSRF-TOKEN: $(xsrf $B)" -w ' HTTP %{http_code}' | sed 's/.*"code":"\([A-Z_]*\)".*HTTP/\1 HTTP/')"
echo "B's own list contains A's order: $(curl -s -b $B "$F/api/orders" | jq --arg id $ID '[.items[] | select(.id == $id)] | length > 0')"

say "4. Roles: a customer is refused operator routes; the operator sees the confirmed order"
echo "B GET /api/ops/orders: $(curl -s -b $B $F/api/ops/orders -w ' HTTP %{http_code}' | sed 's/.*"code":"\([A-Z_]*\)".*HTTP/\1 HTTP/')"
echo "A pays (demo): $(curl -s -b $A -X POST $F/api/demo/orders/$ID/simulate-payment -H "X-XSRF-TOKEN: $(xsrf $A)" | jq -c '{status, confirmationSource}')"
echo "O queue has A's order: $(curl -s -b $O "$F/api/ops/orders?status=CONFIRMED&size=50" | jq --arg id $ID '[.items[] | select(.order.id == $id)] | length == 1')"

say "5. Sign out A; the same session cookie no longer works"
# Keep a copy of the cookies from before sign-out, to replay the old session cookie.
OLD=$(mktemp); cp $A $OLD
echo "A before sign-out (old session $(session $OLD)): HTTP $(curl -s -o /dev/null -w '%{http_code}' -b $OLD $F/api/me)"
echo "A POST /logout: HTTP $(curl -s -o /dev/null -w '%{http_code}' -b $A -c $A -X POST $F/logout -H "X-XSRF-TOKEN: $(xsrf $A)")"
echo "the same old session cookie $(session $OLD) after sign-out: $(curl -s -b $OLD $F/api/me -w ' HTTP %{http_code}' | sed 's/.*"code":"\([A-Z_]*\)".*HTTP/\1 HTTP/')"
rm -f $OLD
echo "anonymous GET /api/orders: $(curl -s $F/api/orders -w ' HTTP %{http_code}' | sed 's/.*"code":"\([A-Z_]*\)".*HTTP/\1 HTTP/')"
