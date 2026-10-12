#!/bin/bash
# On the demo instance (through SSM Session Manager or Run Command): end the
# simulated printer's running print, as a real printer would report it.
#   simulator.sh complete
#   simulator.sh fail "Layer shift"
# The simulator's /dev controls are reachable only on the internal Docker
# network, so a throwaway curl container joins that network for one request.
set -euo pipefail
PRINTER=0d3d0000-0000-4000-8000-000000000101
action=${1:?usage: simulator.sh complete | fail <reason>}
case $action in
  complete) body='' ;;
  fail) body=$(printf '{"reason": "%s"}' "${2:?a failure reason}") ;;
  *) echo "Unknown action: $action" >&2; exit 2 ;;
esac
docker run --rm --network one3d_default curlimages/curl:8.17.0 -s -X POST \
  -H 'Content-Type: application/json' -d "$body" "http://simulator:8090/dev/printers/$PRINTER/$action"
echo
