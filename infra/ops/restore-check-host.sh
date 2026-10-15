#!/bin/bash
# The restore check itself, run on the host where the database container runs:
# the demo instance (sent through SSM by restore-check.sh) or a local machine
# with the Compose stack. See restore-check.sh for what it shows.
#   restore-check-host.sh aws <ops bucket> <artifacts bucket> <region>
#   restore-check-host.sh local <storage url> <artifacts bucket>
set -euo pipefail
MODE=$1
if ! declare -F one3d_lock > /dev/null; then
  . "$(dirname "$0")/../deploy/host-lock.sh"
fi
if [ "$MODE" = aws ]; then
  one3d_lock "${ONE3D_ROOT:-/opt/one3d}"
else
  LOCAL_LOCK=${ONE3D_LOCAL_LOCK_DIR:-${TMPDIR:-/tmp}/one3d-operations-$UID}
  mkdir -p "$LOCAL_LOCK"
  one3d_lock "$LOCAL_LOCK"
fi
DB=one3d-db-1
STAMP=$(date -u +%Y%m%dT%H%M%SZ)
COPY="one3d_restore_check_${STAMP}_$$"
WORK=$(mktemp -d)
created=0
cleanup() {
  status=$?
  trap - EXIT
  if [ "$created" = 1 ]; then
    docker exec "$DB" dropdb -U one3d --if-exists "$COPY" || status=1
  fi
  rm -rf "$WORK"
  exit "$status"
}
trap cleanup EXIT

say() { echo; echo "## $*"; }
q() { docker exec -i "$DB" psql -U one3d -d "$1" -At -F ' | ' -v ON_ERROR_STOP=1 -c "$2"; }
sha() { if command -v sha256sum > /dev/null; then sha256sum "$1"; else shasum -a 256 "$1"; fi | cut -d' ' -f1; }
assert_same() {
  [ "$1" = "$2" ] || { echo "FAILED: $3 differs" >&2; exit 1; }
}

say "1. A fresh backup of the live database ($STAMP)"
docker exec "$DB" pg_dump -U one3d -d one3d --format=custom > "$WORK/backup.dump"
echo "pg_dump (custom format): $(wc -c < "$WORK/backup.dump" | tr -d ' ') bytes"
if [ "$MODE" = aws ]; then
  OPS=$2 ARTIFACTS=$3 REGION=$4
  KEY="backups/one3d-$STAMP-restore-check.dump"
  aws s3 cp "$WORK/backup.dump" "s3://$OPS/$KEY" --region "$REGION" --only-show-errors
  rm "$WORK/backup.dump"
  aws s3 cp "s3://$OPS/$KEY" "$WORK/restore.dump" --region "$REGION" --only-show-errors
  echo "stored in the ops bucket as $KEY, and the copy restored below was read back from S3"
else
  STORAGE=$2 ARTIFACTS=$3
  mv "$WORK/backup.dump" "$WORK/restore.dump"
fi

say "2. Restore into a separate database ($COPY) on the same server"
docker exec "$DB" createdb -U one3d "$COPY"
created=1
docker exec -i "$DB" pg_restore -U one3d -d "$COPY" --no-owner --exit-on-error < "$WORK/restore.dump"
HISTORY="select coalesce(json_agg(json_build_array(version, type, script, checksum, success) order by installed_rank)::text, '[]') from flyway_schema_history"
live_history=$(q one3d "$HISTORY")
restored_history=$(q "$COPY" "$HISTORY")
[ "$live_history" != '[]' ] || { echo "FAILED: no Flyway history" >&2; exit 1; }
assert_same "$live_history" "$restored_history" "Flyway version/script/checksum/success history"
echo "Flyway version/script/checksum/success history matches"

say "3. Row counts, live and restored (the demo was idle, so they should match)"
for table in customer_order order_line order_fact checkout payment payment_event production_job print_attempt \
             printer_command bed_clearance artifact custom_request submission_revision quote pending_work; do
  live=$(q one3d "select count(*) from $table")
  restored=$(q "$COPY" "select count(*) from $table")
  assert_same "$live" "$restored" "$table row count"
  printf '  %-20s live %-4s restored %-4s identical\n' "$table" "$live" "$restored"
done

say "4. One order and what it links to, read from both databases"
ORDER=$(q "$COPY" "select id from customer_order order by (status = 'HANDED_OVER') desc, created_at desc limit 1")
[ -n "$ORDER" ] || { echo "FAILED: no sample order; create a synthetic fixture before the check" >&2; exit 1; }
CHAIN="select o.status, coalesce(o.confirmation_source, '-'), l.product_name, 'recipe revision ' || r.revision_number,
  coalesce((select string_agg(p.source || ' ' || p.outcome || ' ' || p.amount_minor || ' ' || p.currency, '; ')
            from payment p where p.order_id = o.id), 'no payment'),
  coalesce((select 'job ' || j.status from production_job j where j.order_id = o.id), 'no job'),
  coalesce((select string_agg('attempt ' || a.attempt_number || ' ' || a.status || coalesce(' QC ' || a.qc_result, ''),
            '; ' order by a.attempt_number) from print_attempt a join production_job j on j.id = a.job_id
            where j.order_id = o.id), 'no attempts'),
  coalesce((select string_agg('command ' || c.status, '; ') from printer_command c
            join production_job j on j.id = c.job_id where j.order_id = o.id), 'no commands'),
  (select count(*) || ' timeline facts' from order_fact f where f.order_id = o.id)
  from customer_order o join order_line l on l.order_id = o.id join recipe_revision r on r.id = l.recipe_revision_id
  where o.id = '$ORDER'"
restored=$(q "$COPY" "$CHAIN")
live=$(q one3d "$CHAIN")
echo "order ${ORDER:0:8}, restored: $restored"
assert_same "$live" "$restored" "sample order and linked records"
echo "live and restored: identical"

say "5. One checked file: the restored record still names its exact stored version"
record=$(docker exec -i "$DB" psql -U one3d -d "$COPY" -At -F ' ' -v ON_ERROR_STOP=1 -c \
  "select id, storage_key, version_id, sha256 from artifact where status = 'VERIFIED' order by verified_at desc limit 1")
read -r ARTIFACT KEY VERSION SHA256 <<< "$record"
[ -n "$ARTIFACT" ] && [ -n "$VERSION" ] && [ -n "$SHA256" ] || { echo "FAILED: no verified file fixture" >&2; exit 1; }
LIVE_RECORD=$(q one3d "select storage_key || version_id || sha256 from artifact where id = '$ARTIFACT'")
assert_same "$LIVE_RECORD" "$KEY$VERSION$SHA256" "file record"
echo "file ${ARTIFACT:0:8}: version ${VERSION:0:8}…, record in live and restored: identical"
if [ "$MODE" = aws ]; then
  aws s3api get-object --bucket "$ARTIFACTS" --key "$KEY" --version-id "$VERSION" "$WORK/file" --region "$REGION" > /dev/null
else
  curl -sf -o "$WORK/file" "$STORAGE/$ARTIFACTS/$KEY?versionId=$VERSION"
fi
assert_same "$(sha "$WORK/file")" "$SHA256" "stored file SHA-256"
echo "that version read from storage: $(wc -c < "$WORK/file" | tr -d ' ') bytes; SHA-256 matches the restored record: yes"

say "6. Remove the copy"
docker exec "$DB" dropdb -U one3d "$COPY"
created=0
echo "dropped $COPY; the live database and the application were not changed"
