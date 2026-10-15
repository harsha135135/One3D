#!/bin/bash
# Historical instance-replacement automation was for the disposable CP8 demo.
# The recruiter release now preserves this instance and its persistent data.
# Recovery that actually needs replacement requires its own reviewed plan and
# owner approval; the old 'deploy main' path must never be used for V1.
set -euo pipefail
echo "REFUSED: instance replacement is disabled for the frozen recruiter demo." >&2
echo "Use the release recovery runbook; do not replace EC2, EBS or deploy current main." >&2
exit 2
