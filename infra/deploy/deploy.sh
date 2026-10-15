#!/bin/bash
# Runs only the approved manifest's images on the demo instance, after an owner
# promotion or a start of that same frozen release. Never called by CI:
#   /opt/one3d/app/infra/deploy/deploy.sh <commit sha>
# It renders the environment from SSM Parameter Store (never printed), pulls
# the images for that SHA from ECR, obtains the TLS certificate on first run,
# starts the stack and checks that the site answers over HTTPS.
set -euo pipefail

TAG=${1:?usage: deploy.sh <commit sha>}
[[ $TAG =~ ^[0-9a-f]{40}$ ]] || { echo "Not a commit SHA: $TAG" >&2; exit 2; }
ROOT=${ONE3D_ROOT:-/opt/one3d}
APP=$ROOT/app
ENV_FILE=$ROOT/one3d.env
PARAMS=/one3d/demo
. "$(dirname "$0")/host-lock.sh"
one3d_lock "$ROOT"
REGION=$(cat "$ROOT/region")
cd "$APP"
# A historical deploy bundle cannot opt out of release pinning. The updated
# operations envelope includes this validator and the approved manifest.
python3 infra/deploy/release_manifest.py release.json --approve "$TAG" --compose compose.release.json
[ "$(jq -r .target.region release.json)" = "$REGION" ] || { echo "Release region mismatch" >&2; exit 2; }

# --- configuration and secrets from SSM (values never echoed) ---------------
umask 077
tmp=$(mktemp "$ROOT/env.XXXXXX")
trap 'rm -f "$tmp"' EXIT
aws ssm get-parameters-by-path --region "$REGION" --path "$PARAMS/" --with-decryption \
    --query 'Parameters[].[Name,Value]' --output text |
  while IFS=$'\t' read -r name value; do
    key=$(basename "$name" | tr 'a-z-' 'A-Z_')
    printf '%s=%s\n' "$key" "$value"
  done > "$tmp"
printf 'ONE3D_IMAGE_TAG=%s\n' "$TAG" >> "$tmp"
mv "$tmp" "$ENV_FILE"
set -a; . "$ENV_FILE"; set +a
for required in ONE3D_REGISTRY ONE3D_DOMAIN ONE3D_DB_PASSWORD ONE3D_OIDC_CLIENT_SECRET RAZORPAY_KEY_ID \
    ONE3D_GRAFANA_ADMIN_PASSWORD; do
  [ -n "${!required:-}" ] || { echo "Missing parameter for $required under $PARAMS" >&2; exit 3; }
done
[ "$ONE3D_DOMAIN" = "$(jq -r .target.domain release.json)" ] || { echo "Release domain mismatch" >&2; exit 3; }

compose() { docker compose --env-file "$ENV_FILE" -f compose.yaml -f compose.deploy.yaml -f compose.release.json "$@"; }
# Nginx, Prometheus and Grafana are recreated whenever this bundle's
# configuration files differ from the running ones (see the labels in
# compose.deploy.yaml); otherwise they keep running.
export ONE3D_NGINX_CONFIG ONE3D_MONITORING_CONFIG
ONE3D_NGINX_CONFIG=$(cat infra/nginx/bootstrap/* infra/nginx/https/* | sha256sum | cut -c1-16)
ONE3D_MONITORING_CONFIG=$(find infra/monitoring -type f | sort | xargs cat | sha256sum | cut -c1-16)

# --- images: exactly this commit's, from ECR ---------------------------------
# Read the existing database identity before doing anything that can recreate
# it. A missing database is a recovery task, not permission to initialize one.
DB=one3d-db-1
existing_db_image=$(docker inspect --format '{{.Image}}' "$DB")
aws ecr get-login-password --region "$REGION" | docker login --username AWS --password-stdin "$ONE3D_REGISTRY"
compose pull --quiet db simulator backend frontend nginx prometheus grafana certbot

# Frozen V1 must not silently run against a V2 schema. Restore into a separate
# database and review it first if this history differs; never downgrade here.
approved_db_image=$(docker image inspect "$(jq -r .images.db release.json)" --format '{{.Id}}')
[ "$existing_db_image" = "$approved_db_image" ] || { echo "Database image differs from the approved digest; upgrades require a separate plan" >&2; exit 4; }
case $(docker inspect --format '{{.State.Status}}' "$DB") in
  running) ;;
  exited) docker start "$DB" > /dev/null ;;
  *) echo "Existing database is not running or cleanly stopped; inspect it before deployment" >&2; exit 4 ;;
esac
for i in $(seq 1 30); do
  if docker exec "$DB" pg_isready -U one3d -d one3d > /dev/null; then break; fi
  sleep 2
done
actual_versions=$(docker exec "$DB" psql -U one3d -d one3d -At -v ON_ERROR_STOP=1 \
  -c "select string_agg(version, ',' order by installed_rank) from flyway_schema_history where success")
expected_versions=$(jq -r '.database_versions | join(",")' release.json)
[ "$actual_versions" = "$expected_versions" ] || { echo "Database history differs from the release; restore/review separately" >&2; exit 4; }

# --- TLS: obtain the certificate on the first run, renew it when it is due ---
# (Let's Encrypt certificates last 90 days; the demo is started on demand, so
# every deploy and every start checks it.) CERTBOT_STAGING=1 uses the staging
# CA, for rehearsals that must not count against the production rate limits.
staging=""
[ "${CERTBOT_STAGING:-0}" = 1 ] && staging=--staging
renewed=0
if ! compose run --rm --no-deps --entrypoint test certbot -f "/etc/letsencrypt/live/$ONE3D_DOMAIN/fullchain.pem"; then
  ONE3D_NGINX_MODE=bootstrap compose up -d --no-build nginx
  compose run --rm --no-deps certbot certonly --webroot -w /var/www/certbot -d "$ONE3D_DOMAIN" \
    --agree-tos --register-unsafely-without-email --non-interactive ${staging:+"$staging"}
else
  compose run --rm --no-deps certbot renew --webroot -w /var/www/certbot --non-interactive ${staging:+"$staging"} \
    | tee "$ROOT/certbot-renew.log"
  grep -q "Congratulations" "$ROOT/certbot-renew.log" && renewed=1
fi

# --- start or update the stack ------------------------------------------------
previous=$(cat "$ROOT/deployed-tag" 2>/dev/null || true)
ONE3D_NGINX_MODE=https compose up -d --no-build --remove-orphans
[ "$renewed" = 1 ] && compose exec -T nginx nginx -s reload

# --- check: the public name answers over HTTPS through Nginx ------------------
for i in $(seq 1 60); do
  healthy=1
  for service in db backend frontend simulator; do
    id=$(compose ps -q "$service")
    [ -n "$id" ] && [ "$(docker inspect --format '{{.State.Health.Status}}' "$id")" = healthy ] || healthy=0
  done
  for service in nginx prometheus grafana; do
    id=$(compose ps -q "$service")
    [ -n "$id" ] && [ "$(docker inspect --format '{{.State.Status}}' "$id")" = running ] || healthy=0
  done
  if [ "$healthy" = 1 ] && curl -fsS -o /dev/null --resolve "$ONE3D_DOMAIN:443:127.0.0.1" "https://$ONE3D_DOMAIN/api/catalog/configurations"; then
    [ -n "$previous" ] && [ "$previous" != "$TAG" ] && printf '%s\n' "$previous" > "$ROOT/previous-tag"
    printf '%s\n' "$TAG" > "$ROOT/deployed-tag.new"
    mv "$ROOT/deployed-tag.new" "$ROOT/deployed-tag"
    echo "Deployed $TAG (previous: ${previous:-none})"
    exit 0
  fi
  sleep 5
done
echo "Release health failed for $TAG; deployed-tag was not advanced. Inspect the stack before any rollback." >&2
compose ps >&2
exit 1
