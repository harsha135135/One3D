#!/bin/bash
# Checkpoint 5 real-stack check: a custom part from upload to order, through
# the frontend on :3000 (Next.js forwards /api, /oauth2, /login, /logout to the
# backend and /s3 to the S3Mock storage container). Backend with the demo
# profile, PostgreSQL, the mock identity provider and storage from compose.yaml.
# curl cookie jars act as browsers. Needs curl, jq, python3 and docker.
# Signed URLs are kept in variables and never printed.
F=http://localhost:3000
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
  local jar=$1 method=$2 path=$3
  if [ -n "$4" ]; then
    curl -s -b $jar -c $jar -X $method "$F$path" -H "X-XSRF-TOKEN: $(xsrf $jar)" -H 'Content-Type: application/json' -d "$4"
  else
    curl -s -b $jar -c $jar -X $method "$F$path" -H "X-XSRF-TOKEN: $(xsrf $jar)"
  fi
}
code() { jq -r '"\(.code) HTTP \(.status)"'; }
sql() { docker exec one3d-db-1 psql -U one3d -d one3d -At -F ' | ' -c "$1"; }
say() { echo; echo "## $*"; }
# A binary STL of N triangles (80-byte header, little-endian count, 50 bytes each).
stl() { python3 -c "
import os,struct,sys
n=int(sys.argv[1]); sys.stdout.buffer.write(b'one3d checkpoint 5'.ljust(80,b' ')+struct.pack('<I',n)+os.urandom(50*n))" "$1" > "$2"; }
# Starts an upload, PUTs the file to the presigned URL, finalizes; prints the artifact id.
upload() { # upload <jar> <file> <name>
  local size started id url ctype
  size=$(stat -f %z "$2")
  started=$(api $1 POST /api/files/uploads "{\"filename\":\"$3\",\"sizeBytes\":$size}")
  id=$(echo "$started" | jq -r .artifact.id); url=$(echo "$started" | jq -r .upload.url)
  ctype=$(echo "$started" | jq -r '.upload.headers["Content-Type"]')
  echo "$url" > "$WORK/url-$id"
  curl -s -o /dev/null -X PUT --data-binary @"$2" -H "Content-Type: $ctype" "$url" -w "  PUT %{size_upload} bytes to storage: HTTP %{http_code}\n" >&2
  api $1 POST /api/files/$id/finalize | jq -c '{status, pinnedVersion: .versionId}' | sed 's/^/  finalize: /' >&2
  for i in $(seq 1 60); do
    [ "$(api $1 GET /api/files/$id | jq -r .status)" != VERIFYING ] && break; sleep 1
  done
  echo "$id"
}

say "1. Sign in as two customers and an operator"
login $A customer-a; login $B customer-b; login $O operator-1

say "2. Customer A uploads the largest allowed model (straight to storage through :3000/s3)"
stl 419428 $WORK/big.stl
echo "local file: $(stat -f %z $WORK/big.stl) bytes, sha256 $(shasum -a 256 $WORK/big.stl | cut -c1-16)…"
STARTED=$(api $A POST /api/files/uploads '{"filename":"big bracket.stl","sizeBytes":1}')
echo "presigned URL goes to: $(echo "$STARTED" | jq -r .upload.url | sed -E 's/\?.*//; s#uploads/.*#uploads/<random id>#')"
echo "signed query parameters: $(echo "$STARTED" | jq -r .upload.url | sed -E 's/.*\?//' | tr '&' '\n' | cut -d= -f1 | tr '\n' ' ')"
BIG=$(upload $A $WORK/big.stl "big bracket.stl")
echo "after the check: $(api $A GET /api/files/$BIG | jq -c '{status, sizeBytes, sha256: (.sha256[0:16] + "…")}')"
echo "sha256 matches the local file: $([ "$(api $A GET /api/files/$BIG | jq -r .sha256)" = "$(shasum -a 256 $WORK/big.stl | cut -d' ' -f1)" ] && echo yes)"

say "3. Refusals: oversized declaration, wrong type, another customer's file"
echo "declare 20971521 bytes: $(api $A POST /api/files/uploads '{"filename":"huge.stl","sizeBytes":20971521}' | code)"
echo "declare a .obj file: $(api $A POST /api/files/uploads '{"filename":"model.obj","sizeBytes":100}' | code)"
echo "B finalizes A's file: $(api $B POST /api/files/$BIG/finalize | code)"
echo "B reads A's file: $(api $B GET /api/files/$BIG | code)"
echo "B downloads A's file: HTTP $(curl -s -o /dev/null -w '%{http_code}' -b $B $F/api/files/$BIG/download)"
# (Results with JSON bodies are assigned first: macOS bash 3.2 mis-parses \" inside "$(...)".)
R=$(api $B POST /api/requests "{\"artifactId\":\"$BIG\",\"material\":\"PLA\",\"colour\":\"Red\",\"notes\":\"\"}" | code)
echo "B submits A's file: $R"
printf 'solid x\n<html><script>alert(1)</script></html>\n' > $WORK/web.stl
WEB=$(upload $A $WORK/web.stl "page.stl")
echo "HTML pretending to be STL: $(api $A GET /api/files/$WEB | jq -c '{status, rejectionReason}')"

say "4. A sends a request with the checked file"
REQ=$(api $A POST /api/requests "{\"artifactId\":\"$BIG\",\"material\":\"PETG\",\"colour\":\"Orange\",\"notes\":\"Outdoor bracket\"}")
RID=$(echo "$REQ" | jq -r .id)
echo "$REQ" | jq -c '{status, revisions: [.revisions[] | {number, material, colour, file: .file.filename}]}'

say "5. The operator downloads the exact file through :3000"
LOC=$(curl -s -o /dev/null -w '%{redirect_url}' -b $O "$F/api/ops/requests/$RID/revisions/1/file")
echo "302 to a version-pinned link: $(echo "$LOC" | grep -q "versionId=$(sql "SELECT version_id FROM artifact WHERE id = '$BIG'")" && echo yes)"
curl -s -D $WORK/headers -o $WORK/download.stl "$LOC"
grep -iE '^(HTTP|content-type|content-disposition|x-content-type-options|content-security-policy)' $WORK/headers | tr -d '\r' | sed 's/^/  /'
echo "downloaded sha256 equals the checked one: $([ "$(shasum -a 256 $WORK/download.stl | cut -d' ' -f1)" = "$(api $A GET /api/files/$BIG | jq -r .sha256)" ] && echo yes)"
echo "customer B on the operator route: $(curl -s -b $B "$F/api/ops/requests/$RID/revisions/1/file" | code)"

say "6. The operator quotes revision 1"
REV1=$(api $O GET /api/ops/requests/$RID | jq -r '.revisions[-1].id')
Q1=$(api $O POST /api/ops/requests/$RID/quotes "{\"revisionId\":\"$REV1\",\"priceMinor\":149900,\"validDays\":7,\"material\":\"PETG\",\"layerHeightMicrons\":200,\"recipeNotes\":\"4 walls, 25% gyroid\"}")
echo "$Q1" | jq -c '{status, price, expiresAt, recipe: {revisionNumber: .recipe.revisionNumber, material: .recipe.material}}'
Q1=$(echo "$Q1" | jq -r .id)

say "7. A replaces the model: revision 2, review starts again, the quote is withdrawn"
stl 120 $WORK/v2.stl
V2=$(upload $A $WORK/v2.stl "bracket v2.stl")
api $A POST /api/requests/$RID/revisions "{\"artifactId\":\"$V2\",\"material\":\"PETG\",\"colour\":\"Orange\",\"notes\":\"Thicker base\"}" | jq -c '{status, revisions: [.revisions[].number]}'
echo "quote for revision 1 now: $(api $A GET /api/requests/$RID/quotes | jq -c --arg q $Q1 '.[] | select(.id == $q) | {status, acceptable}')"
echo "A accepts the withdrawn quote: $(api $A POST /api/quotes/$Q1/accept | code)"
R=$(api $O POST /api/ops/requests/$RID/quotes "{\"revisionId\":\"$REV1\",\"priceMinor\":149900,\"validDays\":7,\"material\":\"PETG\",\"layerHeightMicrons\":200,\"recipeNotes\":\"\"}" | code)
echo "operator quotes revision 1 again: $R"

say "8. The operator quotes revision 2; B cannot accept it; A accepts it twice"
REV2=$(api $O GET /api/ops/requests/$RID | jq -r '.revisions[-1].id')
Q2=$(api $O POST /api/ops/requests/$RID/quotes "{\"revisionId\":\"$REV2\",\"priceMinor\":159900,\"validDays\":7,\"material\":\"PETG\",\"layerHeightMicrons\":200,\"recipeNotes\":\"4 walls, 25% gyroid\"}" | jq -r .id)
echo "B accepts A's quote: $(api $B POST /api/quotes/$Q2/accept | code)"
ORDER=$(api $A POST /api/quotes/$Q2/accept)
echo "first acceptance: $(echo "$ORDER" | jq -c '{id: .id[0:8], status, line: {source: .line.source, productName: .line.productName, configurationDescription: .line.configurationDescription}, total}')"
OID=$(echo "$ORDER" | jq -r .id)
echo "second acceptance returns the same order: $([ "$(api $A POST /api/quotes/$Q2/accept | jq -r .id)" = "$OID" ] && echo yes)"
echo "orders for this quote: $(sql "SELECT count(*) FROM order_line WHERE quote_id = '$Q2'")"

say "9. Accepted scope, from the order back to the stored file version"
SCOPE="SELECT left(l.quote_id::text,8), left(s.id::text,8), s.revision_number, s.material, s.colour, s.notes,
        left(r.id::text,8), r.material, r.layer_height_microns, r.notes, left(a.id::text,8), a.version_id, left(a.sha256,16)
 FROM order_line l JOIN quote q ON q.id = l.quote_id JOIN submission_revision s ON s.id = q.revision_id
 JOIN artifact a ON a.id = s.artifact_id JOIN recipe_revision r ON r.id = l.recipe_revision_id WHERE l.order_id = '$OID'"
echo "quote | revision | number | material | colour | notes | recipe | material | layer µm | recipe notes | file | version | sha256"
BEFORE=$(sql "$SCOPE"); echo "$BEFORE"
echo "A PUTs different bytes to the same key with the still-valid upload URL:"
stl 120 $WORK/other.stl
curl -s -o /dev/null -X PUT --data-binary @$WORK/other.stl -H 'Content-Type: application/octet-stream' "$(cat $WORK/url-$V2)" -w "  HTTP %{http_code}\n"
echo "versions now stored for that key: $(curl -s "http://127.0.0.1:9090/one3d-artifacts?versions&prefix=uploads/$V2" | grep -o '<VersionId>' | wc -l | tr -d ' ')"
echo "accepted scope unchanged: $([ "$(sql "$SCOPE")" = "$BEFORE" ] && echo yes)"
LOC=$(curl -s -o /dev/null -w '%{redirect_url}' -b $A "$F/api/files/$V2/download")
curl -s -o $WORK/v2-download.stl "$LOC"
echo "A's download is still the checked version: $(cmp -s $WORK/v2-download.stl $WORK/v2.stl && echo yes)"

say "10. The custom order is paid (demo) and released with the quote's recipe"
echo "A pays (demo): $(api $A POST /api/demo/orders/$OID/simulate-payment | jq -c '{status, confirmationSource}')"
JOB=$(api $O POST /api/ops/orders/$OID/release)
echo "job: $(echo "$JOB" | jq -c '{status, recipeRevisionId: .recipeRevisionId[0:8]}')"
echo "job recipe is the accepted quote's recipe: $([ "$(echo "$JOB" | jq -r .recipeRevisionId)" = "$(sql "SELECT recipe_revision_id FROM order_line WHERE order_id = '$OID'")" ] && echo yes)"

say "11. An expired quote cannot be accepted"
stl 10 $WORK/late.stl
LATE=$(upload $A $WORK/late.stl "late.stl")
RID2=$(api $A POST /api/requests "{\"artifactId\":\"$LATE\",\"material\":\"PLA\",\"colour\":\"White\",\"notes\":\"\"}" | jq -r .id)
REV=$(api $O GET /api/ops/requests/$RID2 | jq -r '.revisions[-1].id')
Q3=$(api $O POST /api/ops/requests/$RID2/quotes "{\"revisionId\":\"$REV\",\"priceMinor\":49900,\"validDays\":1,\"material\":\"PLA\",\"layerHeightMicrons\":200,\"recipeNotes\":\"\"}" | jq -r .id)
sql "UPDATE quote SET issued_at = now() - interval '2 days', expires_at = now() - interval '1 second' WHERE id = '$Q3'" > /dev/null
echo "(quote moved into the past with SQL)"
echo "listed as: $(api $A GET /api/requests/$RID2/quotes | jq -c '.[0] | {status, expired, acceptable}')"
echo "A accepts it: $(api $A POST /api/quotes/$Q3/accept | code)"
echo "orders for that quote: $(sql "SELECT count(*) FROM order_line WHERE quote_id = '$Q3'")"
