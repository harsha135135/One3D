#!/bin/bash
# Checkpoint 6 real-stack check: Razorpay TEST MODE and the full journey.
# The frontend is on :3000 and the backend runs with ONE3D_RAZORPAY_ENABLED=true and
# the test keys from the untracked .env (no demo profile). The database, mock
# identity provider, simulator and storage come from compose.yaml.
# - Checkouts create real Razorpay test-mode orders; payments are made in
#   Razorpay's hosted Checkout with its test card (razorpay-test-payment.cjs).
# - Webhooks cannot reach localhost, so sanitized events are replayed here,
#   signed with the configured webhook secret exactly as Razorpay signs them.
#   Real delivery from Razorpay is checked after the deploy (Checkpoint 7).
# No key, secret or signature is printed. Needs curl, jq, openssl, python3,
# node with frontend/node_modules, and docker.
set -u
ROOT=$(cd "$(dirname "$0")/../../../.." && pwd)
HERE=$(cd "$(dirname "$0")" && pwd)
SHOTS="$HERE/../screenshots"
F=http://localhost:3000
SIM=http://127.0.0.1:8090
PRINTER=0d3d0000-0000-4000-8000-000000000101
CFG=0d3d0000-0000-4000-8000-000000000002
WEBHOOK_SECRET=$(awk -F= '/^RAZORPAY_WEBHOOK_SECRET=/{sub(/^RAZORPAY_WEBHOOK_SECRET=/, ""); print}' "$ROOT/.env")
RUN=$(date +%s)
A=$(mktemp); B=$(mktemp); O=$(mktemp); WORK=$(mktemp -d)
trap 'rm -rf $A $B $O $WORK' EXIT

xsrf() { awk '$6 == "XSRF-TOKEN" { print $7 }' "$1"; }
login() {
  local auth callback
  auth=$(curl -s -c "$1" -b "$1" -o /dev/null -w '%{redirect_url}' $F/oauth2/authorization/oidc)
  callback=$(curl -s -o /dev/null -w '%{redirect_url}' -X POST "$auth" --data-urlencode "username=$2" --data-urlencode "claims=")
  curl -s -c "$1" -b "$1" -o /dev/null -w "sign-in as $2: HTTP %{http_code}\n" "$callback"
}
api() { # api <jar> <method> <path> [json]
  if [ -n "${4:-}" ]; then
    curl -s -b $1 -c $1 -X $2 "$F$3" -H "X-XSRF-TOKEN: $(xsrf $1)" -H 'Content-Type: application/json' -d "$4"
  else
    curl -s -b $1 -c $1 -X $2 "$F$3" -H "X-XSRF-TOKEN: $(xsrf $1)"
  fi
}
code() { jq -r '"\(.code) HTTP \(.status)"'; }
sql() { docker exec one3d-db-1 psql -U one3d -d one3d -At -F ' | ' -c "$1"; }
say() { echo; echo "## $*"; }
# A Razorpay-shaped event, sanitized (no real contact details).
event() { # event <type> <payment id> <razorpay order id> <amount> <status>
  printf '{"entity":"event","account_id":"acc_sanitized","event":"%s","contains":["payment"],"payload":{"payment":{"entity":{"id":"%s","entity":"payment","amount":%s,"currency":"INR","status":"%s","order_id":"%s","captured":%s,"method":"card","created_at":%s}}},"created_at":%s}' \
    "$1" "$2" "$4" "$5" "$3" "$([ "$5" = captured ] && echo true || echo false)" "$(date +%s)" "$(date +%s)"
}
# deliver <body> <event id> [secret]: POST to the webhook through :3000, with no session or CSRF token.
deliver() {
  local secret=${3:-$WEBHOOK_SECRET} signature
  printf '%s' "$1" > $WORK/body.json
  signature=$(openssl dgst -sha256 -hmac "$secret" -hex < $WORK/body.json | sed 's/^.* //')
  curl -s -X POST $F/api/payments/razorpay/webhook -H 'Content-Type: application/json' \
    -H "X-Razorpay-Event-Id: $2" -H "X-Razorpay-Signature: $signature" --data-binary @$WORK/body.json
}
pay_in_checkout() { # pay_in_checkout <order id> <username> <screenshot dir>
  mkdir -p "$3"
  (cd "$ROOT/frontend" && NODE_PATH="$ROOT/frontend/node_modules" node "$HERE/razorpay-test-payment.cjs" "$3" "$1" "$2") \
    | grep -vE '^\[|^BANK (INPUTS|TEXT)' | sed 's/^/  checkout: /'
}
stl() { python3 -c "
import os,struct,sys
n=int(sys.argv[1]); sys.stdout.buffer.write(b'one3d checkpoint 6'.ljust(80,b' ')+struct.pack('<I',n)+os.urandom(50*n))" "$1" > "$2"; }
produce() { # produce <order id> <label>: release, print on the simulator, QC, ready, handed over
  local job attempt cycle
  job=$(api $O POST /api/ops/orders/$1/release | jq -r .id)
  cycle=$(api $O GET /api/ops/printers | jq -r ".[] | select(.id == \"$PRINTER\") | .bedCycleAttemptId // empty")
  if [ -n "$cycle" ]; then
    echo "  $2: operator clears the bed of the previous print: $(api $O POST /api/ops/printers/$PRINTER/clear-bed "{\"attemptId\":\"$cycle\"}" | jq -r .bedState)"
  fi
  attempt=$(api $O POST /api/ops/jobs/$job/attempts "{\"printerId\":\"$PRINTER\"}" | jq -r '.attempts[-1].id')
  for i in $(seq 1 15); do [ "$(api $O GET /api/ops/jobs/$job | jq -r '.attempts[-1].status')" = RUNNING ] && break; sleep 1; done
  echo "  $2: released, attempt $(api $O GET /api/ops/jobs/$job | jq -r '.attempts[-1].status') on the simulator"
  curl -s -o /dev/null -X POST $SIM/dev/printers/$PRINTER/complete
  for i in $(seq 1 15); do [ "$(api $O GET /api/ops/jobs/$job | jq -r .status)" = AWAITING_QC ] && break; sleep 1; done
  echo "  $2: QC $(api $O POST /api/ops/attempts/$attempt/qc '{"result":"PASSED","notes":"Whole-part check"}' | jq -r .status)," \
    "ready: $(api $O POST /api/ops/orders/$1/ready-for-handoff | jq -r .status)," \
    "handed over: $(api $O POST /api/ops/orders/$1/handed-over | jq -r .status)"
}

say "1. Sign in as two customers and an operator"
login $A cp6-customer-a-$RUN; login $B cp6-customer-b-$RUN; login $O operator-cp6-$RUN
echo "payment mode for A: $(api $A GET /api/me | jq -r .paymentMode)"
echo "demo shortcut in this mode: $(curl -s -o /dev/null -w 'HTTP %{http_code}' -b $A -X POST $F/api/demo/orders/$CFG/simulate-payment -H "X-XSRF-TOKEN: $(xsrf $A)")"

say "2. A orders from the catalog and opens a test-mode checkout (a real Razorpay order)"
CAT=$(api $A POST /api/orders "{\"productConfigurationId\":\"$CFG\",\"quantity\":1}" | jq -r .id)
C1=$(api $A POST /api/orders/$CAT/checkout)
echo "$C1" | jq -c '{providerOrderId, keyId: (.keyId[0:9] + "…"), amountMinor, currency, testMode, expiresAt}'
echo "opening again reuses it: $([ "$(api $A POST /api/orders/$CAT/checkout | jq -r .providerOrderId)" = "$(echo "$C1" | jq -r .providerOrderId)" ] && echo yes)"
echo "B opens a checkout for A's order: $(api $B POST /api/orders/$CAT/checkout | code)"
echo "B asks about A's payment: $(api $B POST /api/orders/$CAT/payment-check | code)"
echo "A asks before paying (the backend asks Razorpay): $(api $A POST /api/orders/$CAT/payment-check | jq -c .)"

say "3. A pays in Razorpay's hosted Checkout (test card, test bank page)"
pay_in_checkout $CAT cp6-customer-a-$RUN "$SHOTS/catalog"
echo "order: $(api $A GET /api/orders/$CAT | jq -c '{status, confirmationSource}')"
sql "SELECT 'payment: ' || provider_payment_id || ' | ' || provider_order_id || ' | ' || source || ' | ' || outcome || ' | ' || amount_minor || ' ' || currency FROM payment WHERE order_id = '$CAT'"
echo "production job after payment alone: $(api $O GET /api/ops/orders/$CAT | jq -c '{jobId}')"
PAID=$(sql "SELECT provider_payment_id FROM payment WHERE order_id = '$CAT' AND outcome = 'APPLIED'")
PROV=$(echo "$C1" | jq -r .providerOrderId)

say "4. Webhook checks on the running server (events signed locally with the configured secret)"
BODY=$(event payment.captured "$PAID" "$PROV" 49900 captured)
echo "no signature: $(curl -s -X POST $F/api/payments/razorpay/webhook -H 'Content-Type: application/json' -H 'X-Razorpay-Event-Id: evt_cp6_nosig' --data-binary "$BODY" | code)"
echo "signed with another secret: $(deliver "$BODY" evt_cp6_forged_$RUN not-the-secret | code)"
echo "the real payment, reported again by a webhook: $(deliver "$BODY" evt_cp6_dup_$RUN | jq -r .outcome)"
echo "the same event delivered twice: $(deliver "$BODY" evt_cp6_dup_$RUN | jq -r .outcome)"
echo "payment.authorized for another payment: $(deliver "$(event payment.authorized pay_cp6auth$RUN "$PROV" 49900 authorized)" evt_cp6_auth_$RUN | jq -r .outcome)"
echo "a second, distinct captured payment for the paid order: $(deliver "$(event payment.captured pay_cp6second$RUN "$PROV" 49900 captured)" evt_cp6_second_$RUN | jq -r .outcome)"
echo "payments recorded for the order: $(sql "SELECT string_agg(outcome || coalesce(':' || review_reason, ''), ', ' ORDER BY recorded_at) FROM payment WHERE order_id = '$CAT'")"
OTHER=$(api $A POST /api/orders "{\"productConfigurationId\":\"$CFG\",\"quantity\":1}" | jq -r .id)
PROV2=$(api $A POST /api/orders/$OTHER/checkout | jq -r .providerOrderId)
echo "a captured payment of the wrong amount for another order: $(deliver "$(event payment.captured pay_cp6amount$RUN "$PROV2" 100 captured)" evt_cp6_amount_$RUN | jq -r .outcome)," \
  "that order is still $(api $A GET /api/orders/$OTHER | jq -r .status)"
echo "a captured payment for a Razorpay order that is not ours: $(deliver "$(event payment.captured pay_cp6unknown$RUN order_notours$RUN 49900 captured)" evt_cp6_unknown_$RUN | jq -r .outcome)"
echo "GET on the webhook path: HTTP $(curl -s -o /dev/null -w '%{http_code}' $F/api/payments/razorpay/webhook)"

say "5. The operator sees what needs a manual check and records a decision"
api $O GET /api/ops/payments | jq -r ".[] | select(.providerPaymentId | test(\"cp6.*$RUN\")) | \"  \\(.providerPaymentId[0:13])… \\(.reviewReason)\""
REVIEW=$(api $O GET /api/ops/payments | jq -r ".[] | select(.providerPaymentId == \"pay_cp6second$RUN\") | .id")
echo "resolve the second payment: $(api $O POST /api/ops/payments/$REVIEW/resolve '{"note":"Duplicate test payment; would be refunded in the Razorpay dashboard"}' | jq "[.[] | select(.id == \"$REVIEW\")] | length") open reviews left for it"
echo "a customer on the operator route: $(api $A GET /api/ops/payments | code)"

say "6. A custom part: upload, request, quote, accept, pay in Checkout"
stl 60 $WORK/part.stl
SIZE=$(stat -f %z $WORK/part.stl)
UP=$(api $A POST /api/files/uploads "{\"filename\":\"wall hook.stl\",\"sizeBytes\":$SIZE}")
FILE=$(echo "$UP" | jq -r .artifact.id)
curl -s -o /dev/null -X PUT --data-binary @$WORK/part.stl -H 'Content-Type: application/octet-stream' "$(echo "$UP" | jq -r .upload.url)"
api $A POST /api/files/$FILE/finalize > /dev/null
for i in $(seq 1 30); do [ "$(api $A GET /api/files/$FILE | jq -r .status)" = VERIFIED ] && break; sleep 1; done
RID=$(api $A POST /api/requests "{\"artifactId\":\"$FILE\",\"material\":\"PETG\",\"colour\":\"White\",\"notes\":\"Holds 2 kg\"}" | jq -r .id)
REV=$(api $O GET /api/ops/requests/$RID | jq -r '.revisions[-1].id')
QUOTE=$(api $O POST /api/ops/requests/$RID/quotes "{\"revisionId\":\"$REV\",\"priceMinor\":34900,\"validDays\":3,\"material\":\"PETG\",\"layerHeightMicrons\":200,\"recipeNotes\":\"4 walls\"}")
CUS=$(api $A POST /api/quotes/$(echo "$QUOTE" | jq -r .id)/accept | jq -r .id)
echo "custom order: $(api $A GET /api/orders/$CUS | jq -c '{status, item: .line.productName, total: .total.amountMinor, payBy: .paymentWindowEndsAt}'), quote valid until $(echo "$QUOTE" | jq -r .expiresAt)"
pay_in_checkout $CUS cp6-customer-a-$RUN "$SHOTS/custom"
echo "custom order: $(api $A GET /api/orders/$CUS | jq -c '{status, confirmationSource}')"

say "7. Both orders through the same production workflow"
produce $CAT "catalog order"
produce $CUS "custom order"

say "8. The customer's timelines"
for ORDER in $CAT $CUS; do
  echo "$(api $A GET /api/orders/$ORDER | jq -r .line.productName):"
  api $A GET /api/orders/$ORDER/timeline | jq -r '.[] | "  \(.kind)\(if .detail then " (" + (.detail | sub("pay_[A-Za-z0-9]+"; "pay_…")) + ")" else "" end)"'
done
