#!/bin/bash
# Deploys one commit's images on the demo instance. Run as root by GitHub
# Actions through SSM Run Command, after the bundle for that commit has been
# unpacked into /opt/one3d/app:
#   /opt/one3d/app/infra/deploy/deploy.sh <commit sha>
# It renders the environment from SSM Parameter Store (never printed), pulls
# the images for that SHA from ECR, obtains the TLS certificate on first run,
# starts the stack and checks that the site answers over HTTPS.
set -euo pipefail

TAG=${1:?usage: deploy.sh <commit sha>}
[[ $TAG =~ ^[0-9a-f]{40}$ ]] || { echo "Not a commit SHA: $TAG" >&2; exit 2; }
APP=/opt/one3d/app
ENV_FILE=/opt/one3d/one3d.env
PARAMS=/one3d/demo
REGION=$(cat /opt/one3d/region)
cd "$APP"

# --- configuration and secrets from SSM (values never echoed) ---------------
umask 077
tmp=$(mktemp /opt/one3d/env.XXXXXX)
aws ssm get-parameters-by-path --region "$REGION" --path "$PARAMS/" --with-decryption \
    --query 'Parameters[].[Name,Value]' --output text |
  while IFS=$'\t' read -r name value; do
    key=$(basename "$name" | tr 'a-z-' 'A-Z_')
    printf '%s=%s\n' "$key" "$value"
  done > "$tmp"
printf 'ONE3D_IMAGE_TAG=%s\n' "$TAG" >> "$tmp"
mv "$tmp" "$ENV_FILE"
set -a; . "$ENV_FILE"; set +a
for required in ONE3D_REGISTRY ONE3D_DOMAIN ONE3D_DB_PASSWORD ONE3D_OIDC_CLIENT_SECRET RAZORPAY_KEY_ID; do
  [ -n "${!required:-}" ] || { echo "Missing parameter for $required under $PARAMS" >&2; exit 3; }
done

compose() { docker compose --env-file "$ENV_FILE" -f compose.yaml -f compose.deploy.yaml "$@"; }
# Nginx is recreated whenever this bundle's templates differ from the running
# ones (see the label in compose.deploy.yaml); otherwise it keeps running.
export ONE3D_NGINX_CONFIG
ONE3D_NGINX_CONFIG=$(cat infra/nginx/bootstrap/* infra/nginx/https/* | sha256sum | cut -c1-16)

# --- images: exactly this commit's, from ECR ---------------------------------
aws ecr get-login-password --region "$REGION" | docker login --username AWS --password-stdin "$ONE3D_REGISTRY"
compose pull --quiet db simulator backend frontend nginx

# --- TLS: obtain the certificate on the first run, renew it when it is due ---
# (Let's Encrypt certificates last 90 days; the demo is started on demand, so
# every deploy and every start checks it.) CERTBOT_STAGING=1 uses the staging
# CA, for rehearsals that must not count against the production rate limits.
staging=()
[ "${CERTBOT_STAGING:-0}" = 1 ] && staging=(--staging)
renewed=0
if ! compose run --rm --no-deps --entrypoint test certbot -f "/etc/letsencrypt/live/$ONE3D_DOMAIN/fullchain.pem"; then
  ONE3D_NGINX_MODE=bootstrap compose up -d --no-build nginx
  compose run --rm --no-deps certbot certonly --webroot -w /var/www/certbot -d "$ONE3D_DOMAIN" \
    --agree-tos --register-unsafely-without-email --non-interactive "${staging[@]}"
else
  compose run --rm --no-deps certbot renew --webroot -w /var/www/certbot --non-interactive "${staging[@]}" \
    | tee /tmp/certbot-renew.log
  grep -q "Congratulations" /tmp/certbot-renew.log && renewed=1
fi

# --- start or update the stack ------------------------------------------------
previous=$(cat /opt/one3d/deployed-tag 2>/dev/null || true)
ONE3D_NGINX_MODE=https compose up -d --no-build --remove-orphans
[ "$renewed" = 1 ] && compose exec -T nginx nginx -s reload
[ -n "$previous" ] && [ "$previous" != "$TAG" ] && echo "$previous" > /opt/one3d/previous-tag
echo "$TAG" > /opt/one3d/deployed-tag

# --- check: the public name answers over HTTPS through Nginx ------------------
for i in $(seq 1 60); do
  if curl -fsS -o /dev/null --resolve "$ONE3D_DOMAIN:443:127.0.0.1" "https://$ONE3D_DOMAIN/api/catalog/configurations"; then
    echo "Deployed $TAG (previous: ${previous:-none})"
    docker image prune -af --filter "until=72h" > /dev/null || true
    exit 0
  fi
  sleep 5
done
echo "The site did not answer after deploying $TAG" >&2
compose ps >&2
exit 1
