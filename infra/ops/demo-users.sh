#!/bin/bash
# Creates the synthetic demo users in the demo's Cognito user pool and enrols
# their TOTP authenticators (MFA is required for everyone in the pool), as the
# deployment user. Nothing secret is printed: each user's sign-in email,
# password and TOTP secret go to SSM Parameter Store as a SecureString under
# /one3d/demo-users/<role> (outside /one3d/demo/, so they never reach the
# backend's environment). Run again to reset a user.
#   infra/ops/demo-users.sh customer   -> customer.demo@example.com
#   infra/ops/demo-users.sh operator   -> operator.demo@example.com (operators group)
# To use one: aws ssm get-parameter --name /one3d/demo-users/<role> --with-decryption
# and add its "totp_uri" to an authenticator app.
set -euo pipefail
export AWS_PROFILE=${AWS_PROFILE:-one3d-deployer} AWS_REGION=${AWS_REGION:-ap-south-1} AWS_PAGER=""
ROLE=${1:?usage: demo-users.sh customer|operator}
case $ROLE in customer|operator) ;; *) echo "customer or operator" >&2; exit 2 ;; esac
EMAIL="$ROLE.demo@example.com"
TF=$(cd "$(dirname "$0")/../terraform/demo" && pwd)
POOL=$(terraform -chdir="$TF" output -raw cognito_user_pool_id)
CLIENT=$(aws ssm get-parameter --name /one3d/demo/one3d-oidc-client-id --query Parameter.Value --output text)
SECRETS=$(mktemp -d); chmod 700 "$SECRETS"; trap 'rm -rf "$SECRETS"' EXIT
aws ssm get-parameter --name /one3d/demo/one3d-oidc-client-secret --with-decryption \
  --query Parameter.Value --output text > "$SECRETS/client-secret"

py() { python3 - "$SECRETS" "$@"; }
# SECRET_HASH = Base64(HMAC-SHA256(client secret, username + client id))
secret_hash() { py "$EMAIL" "$CLIENT" <<'EOF'
import base64, hashlib, hmac, sys
secret = open(sys.argv[1] + "/client-secret").read().strip().encode()
print(base64.b64encode(hmac.new(secret, (sys.argv[2] + sys.argv[3]).encode(), hashlib.sha256).digest()).decode())
EOF
}
# RFC 6238 TOTP (SHA-1, 30 s, 6 digits) from the base32 secret in $SECRETS/totp.
totp() { py <<'EOF'
import base64, hashlib, hmac, struct, sys, time
raw = open(sys.argv[1] + "/totp").read().strip().upper()
key = base64.b32decode(raw + "=" * (-len(raw) % 8))
counter = struct.pack(">Q", int(time.time()) // 30)
digest = hmac.new(key, counter, hashlib.sha1).digest()
offset = digest[-1] & 0x0F
print("%06d" % ((struct.unpack(">I", digest[offset:offset + 4])[0] & 0x7FFFFFFF) % 1000000))
EOF
}

# A fresh password: 24 characters with upper case, lower case and digits.
py <<'EOF' > /dev/null
import secrets, string, sys
alphabet = string.ascii_letters + string.digits
while True:
    p = "".join(secrets.choice(alphabet) for _ in range(24))
    if any(c.islower() for c in p) and any(c.isupper() for c in p) and any(c.isdigit() for c in p):
        break
open(sys.argv[1] + "/password", "w").write(p)
EOF

if aws cognito-idp admin-get-user --user-pool-id "$POOL" --username "$EMAIL" > /dev/null 2>&1; then
  aws cognito-idp admin-delete-user --user-pool-id "$POOL" --username "$EMAIL"
  echo "reset existing $ROLE user"
fi
aws cognito-idp admin-create-user --user-pool-id "$POOL" --username "$EMAIL" --message-action SUPPRESS \
  --user-attributes Name=email,Value="$EMAIL" Name=email_verified,Value=true > /dev/null
aws cognito-idp admin-set-user-password --cli-input-json "$(py "$POOL" "$EMAIL" <<'EOF'
import json, sys
print(json.dumps({"UserPoolId": sys.argv[2], "Username": sys.argv[3], "Permanent": True,
                  "Password": open(sys.argv[1] + "/password").read()}))
EOF
)" > /dev/null
[ "$ROLE" = operator ] && aws cognito-idp admin-add-user-to-group --user-pool-id "$POOL" --username "$EMAIL" --group-name operators

# Sign in once as the user (admin API) to reach the MFA_SETUP challenge, then
# associate and verify an authenticator, exactly as the hosted page would.
auth=$(aws cognito-idp admin-initiate-auth --user-pool-id "$POOL" --client-id "$CLIENT" \
  --auth-flow ADMIN_USER_PASSWORD_AUTH --cli-input-json "$(py "$EMAIL" "$(secret_hash)" <<'EOF'
import json, sys
print(json.dumps({"AuthParameters": {"USERNAME": sys.argv[2], "SECRET_HASH": sys.argv[3],
                                     "PASSWORD": open(sys.argv[1] + "/password").read()}}))
EOF
)")
[ "$(jq -r .ChallengeName <<< "$auth")" = MFA_SETUP ] || { echo "Expected MFA_SETUP, got $(jq -r .ChallengeName <<< "$auth")" >&2; exit 1; }
assoc=$(aws cognito-idp associate-software-token --session "$(jq -r .Session <<< "$auth")")
jq -r .SecretCode <<< "$assoc" > "$SECRETS/totp"
verified=$(aws cognito-idp verify-software-token --session "$(jq -r .Session <<< "$assoc")" \
  --user-code "$(totp)" --friendly-device-name demo-script)
[ "$(jq -r .Status <<< "$verified")" = SUCCESS ] || { echo "TOTP verification failed" >&2; exit 1; }
aws cognito-idp admin-respond-to-auth-challenge --user-pool-id "$POOL" --client-id "$CLIENT" \
  --challenge-name MFA_SETUP --session "$(jq -r .Session <<< "$verified")" \
  --challenge-responses "USERNAME=$EMAIL,SECRET_HASH=$(secret_hash)" > /dev/null
aws cognito-idp admin-set-user-mfa-preference --user-pool-id "$POOL" --username "$EMAIL" \
  --software-token-mfa-settings Enabled=true,PreferredMfa=true

# Store the sign-in details (never printed).
py "$ROLE" "$EMAIL" <<'EOF' > "$SECRETS/param.json"
import json, sys, urllib.parse
d = sys.argv[1]
totp = open(d + "/totp").read().strip()
email = sys.argv[3]
value = {"email": email, "password": open(d + "/password").read(), "totp_secret": totp,
         "totp_uri": "otpauth://totp/One3D%20demo:" + urllib.parse.quote(email) + "?secret=" + totp + "&issuer=One3D%20demo"}
print(json.dumps({"Name": "/one3d/demo-users/" + sys.argv[2], "Type": "SecureString", "Overwrite": True,
                  "Description": "Synthetic One3D demo user (Cognito sign-in with TOTP)", "Value": json.dumps(value)}))
EOF
aws ssm put-parameter --cli-input-json "file://$SECRETS/param.json" > /dev/null
echo "$ROLE user ready: $EMAIL (groups: $(aws cognito-idp admin-list-groups-for-user --user-pool-id "$POOL" --username "$EMAIL" --query 'Groups[].GroupName' --output text)); sign-in details in /one3d/demo-users/$ROLE"
