#!/bin/bash
# One host operation at a time. The lock belongs to the open file description,
# so children inherit FD 9 and can confirm the same lock without deadlocking.
# Python's standard-library flock also works for local checks on macOS.
one3d_lock() {
  one3d_lock_root=$1
  if [ "${ONE3D_OPERATION_LOCK_HELD:-0}" != 1 ]; then
    exec 9>"$one3d_lock_root/operation.lock"
  fi
  python3 - "$one3d_lock_root/operation.lock" <<'PY'
import fcntl, os, sys
try:
    descriptor = os.fstat(9)
    expected = os.stat(sys.argv[1])
    if (descriptor.st_dev, descriptor.st_ino) != (expected.st_dev, expected.st_ino):
        raise OSError("inherited descriptor does not name the operation lock")
    fcntl.flock(9, fcntl.LOCK_EX | fcntl.LOCK_NB)
except OSError as error:
    print(f"One3D operation refused: another operation owns the lock, or its descriptor is invalid ({error})", file=sys.stderr)
    sys.exit(1)
PY
  one3d_lock_status=$?
  [ "$one3d_lock_status" = 0 ] || return "$one3d_lock_status"
  export ONE3D_OPERATION_LOCK_HELD=1
  # Stop releases its shell lock just before the owner asks EC2 to stop. Keep
  # other operations from starting in that gap. A fresh boot clears the intent.
  if [ -f "$one3d_lock_root/shutdown-requested" ]; then
    one3d_current_boot=$(cat /proc/sys/kernel/random/boot_id) || return 1
    if [ "$(cat "$one3d_lock_root/shutdown-requested")" = "$one3d_current_boot" ]; then
      echo "One3D shutdown is pending; finish the stop or explicitly cancel it before another operation" >&2
      return 1
    fi
    rm "$one3d_lock_root/shutdown-requested"
  fi
}
