#!/bin/bash
# Checkpoint 7: the deployed demo from the outside (no credentials), plus the
# bucket's public face. Needs curl, nc, dig, openssl and the deployment user's
# AWS session only for the bucket name (terraform output).
D=demo.one3d.store
IP=$(dig +short A $D @1.1.1.1 | head -1)
BUCKET=$(terraform -chdir="$(dirname "$0")/../../../../infra/terraform/demo" output -raw artifacts_bucket)
say() { echo; echo "## $*"; }

say "1. DNS and TLS"
echo "A $D -> $IP (1.1.1.1); AAAA: $(dig +short AAAA $D @1.1.1.1 | wc -l | tr -d ' ') records"
echo | openssl s_client -connect $D:443 -servername $D 2>/dev/null | openssl x509 -noout -issuer -subject -enddate
echo "TLS 1.1 refused: $(echo | openssl s_client -connect $D:443 -servername $D -tls1_1 2>&1 | grep -qiE 'alert|no protocols|wrong version|handshake failure' && echo yes || echo no)"

say "2. HTTP redirects to HTTPS; security headers"
curl -s -o /dev/null -w "http://$D/orders -> %{http_code} %{redirect_url}\n" http://$D/orders
curl -sI https://$D/ | tr -d '\r' | grep -iE '^(HTTP|strict-transport|x-content-type|x-frame|referrer-policy|content-security-policy)' | cut -c1-140

say "3. Only 80 and 443 are reachable (TCP connect from outside, 3 s timeout)"
for port in 22 80 443 3000 5432 8080 8090 9090; do
  if nc -z -G 3 -w 3 $IP $port 2>/dev/null; then echo "  $port open"; else echo "  $port closed/filtered"; fi
done

say "4. Anonymous API access"
curl -s -o /dev/null -w "GET /api/catalog/configurations: %{http_code}\n" https://$D/api/catalog/configurations
echo "GET /api/orders: $(curl -s https://$D/api/orders -w ' HTTP %{http_code}' | sed 's/.*"code":"\([A-Z_]*\)".*HTTP/\1 HTTP/')"
echo "GET /api/ops/orders: $(curl -s https://$D/api/ops/orders -w ' HTTP %{http_code}' | sed 's/.*"code":"\([A-Z_]*\)".*HTTP/\1 HTTP/')"
echo "POST webhook without a signature: $(curl -s -X POST https://$D/api/payments/razorpay/webhook -H 'Content-Type: application/json' -H 'X-Razorpay-Event-Id: evt_probe' -d '{}' -w ' HTTP %{http_code}' | sed 's/.*"code":"\([A-Z_]*\)".*HTTP/\1 HTTP/')"
echo "POST /api/orders without session or CSRF: HTTP $(curl -s -o /dev/null -w '%{http_code}' -X POST https://$D/api/orders -H 'Content-Type: application/json' -d '{}')"
echo "2 MB body to the webhook: HTTP $(head -c 2097152 /dev/zero | curl -s -o /dev/null -w '%{http_code}' -X POST https://$D/api/payments/razorpay/webhook -H 'Content-Type: application/json' --data-binary @-)"

say "5. Sign-in goes to Cognito over HTTPS with Secure cookies"
curl -s -D - -o /dev/null https://$D/oauth2/authorization/oidc | tr -d '\r' | grep -iE '^(HTTP|location|set-cookie)' \
  | sed -E 's/(state|nonce|code_challenge)=[^&]+/\1=…/g; s/(JSESSIONID|XSRF-TOKEN)=[^;]+/\1=…/' | cut -c1-200

say "6. The file bucket is private"
echo "anonymous list: HTTP $(curl -s -o /dev/null -w '%{http_code}' https://$BUCKET.s3.ap-south-1.amazonaws.com/)"
echo "anonymous object GET: HTTP $(curl -s -o /dev/null -w '%{http_code}' https://$BUCKET.s3.ap-south-1.amazonaws.com/uploads/anything)"
echo "plain HTTP to the bucket: HTTP $(curl -s -o /dev/null -w '%{http_code}' http://$BUCKET.s3.ap-south-1.amazonaws.com/uploads/anything)"
echo "CORS preflight from https://$D: $(curl -s -o /dev/null -D - -X OPTIONS https://$BUCKET.s3.ap-south-1.amazonaws.com/uploads/x -H "Origin: https://$D" -H 'Access-Control-Request-Method: PUT' -H 'Access-Control-Request-Headers: content-type' | tr -d '\r' | grep -iE '^(HTTP|access-control-allow-origin)' | tr '\n' ' ')"
echo "CORS preflight from https://attacker.example: $(curl -s -o /dev/null -D - -X OPTIONS https://$BUCKET.s3.ap-south-1.amazonaws.com/uploads/x -H 'Origin: https://attacker.example' -H 'Access-Control-Request-Method: PUT' | tr -d '\r' | grep -iE '^(HTTP|access-control-allow-origin)' | tr '\n' ' ')"
