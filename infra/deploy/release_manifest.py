#!/usr/bin/env python3
"""Validate release pins without cloud access; render the Compose image override."""
import argparse
import hashlib
import json
from pathlib import Path, PurePosixPath
import re
import sys
import tarfile

SERVICES = {"backend", "frontend", "simulator", "db", "nginx", "prometheus", "grafana", "certbot"}
SHA = re.compile(r"[0-9a-f]{40}")
DIGEST = re.compile(r"[a-zA-Z0-9./_-]+@sha256:[0-9a-f]{64}")


def require(condition, message):
    if not condition:
        raise ValueError(message)


def load_manifest(path):
    data = json.loads(Path(path).read_text())
    require(data.get("schema_version") == 1, "schema_version must be 1")
    require(data.get("release") == "v1.0.0", "This promotion path is restricted to v1.0.0")
    for field in ("commit", "operations_commit"):
        require(isinstance(data.get(field), str) and SHA.fullmatch(data[field]), f"{field} must be a full commit SHA")
    target = data["target"]
    for key, pattern in {"account_id": r"[0-9]{12}", "region": r"ap-south-1",
                         "instance_id": r"i-[0-9a-f]{8,17}", "domain": r"demo\.one3d\.store"}.items():
        require(isinstance(target.get(key), str) and re.fullmatch(pattern, target[key]), f"Invalid target.{key}")
    bundle = data["bundle"]
    require(re.fullmatch(r"one3d-[a-z0-9.-]+", bundle["bucket"]), "Invalid operations bucket")
    require(re.fullmatch(r"deploy/[A-Za-z0-9/_.-]+\.tar\.gz", bundle["key"])
            and ".." not in PurePosixPath(bundle["key"]).parts, "Invalid bundle key")
    require(isinstance(bundle.get("version_id"), str) and bundle["version_id"] not in ("", "null", "None")
            and not any(c.isspace() for c in bundle["version_id"]), "An exact S3 VersionId is required")
    require(re.fullmatch(r"[0-9a-f]{64}", bundle["sha256"]), "An exact bundle SHA-256 is required")
    require(set(data["images"]) == SERVICES, "Pin all eight runtime/tool images")
    for service, image in data["images"].items():
        require(isinstance(image, str) and DIGEST.fullmatch(image), f"{service} must use repository@sha256:digest")
        if service in ("backend", "frontend", "simulator"):
            prefix = f'{target["account_id"]}.dkr.ecr.{target["region"]}.amazonaws.com/one3d-{service}@'
            require(image.startswith(prefix), f"{service} must name this account's ECR repository")
    versions = data.get("database_versions")
    require(isinstance(versions, list) and bool(versions)
            and all(isinstance(v, str) and re.fullmatch(r"[0-9]+(?:\.[0-9]+)*", v) for v in versions)
            and len(set(versions)) == len(versions), "database_versions must name the reviewed Flyway history in installed order")
    return data


def verify_bundle(path, expected):
    require(hashlib.sha256(Path(path).read_bytes()).hexdigest() == expected, "Bundle SHA-256 mismatch")
    with tarfile.open(path, "r:gz") as archive:
        for member in archive.getmembers():
            name = PurePosixPath(member.name)
            require(not name.is_absolute() and ".." not in name.parts, "Unsafe path in deployment bundle")
            require(member.isfile() or member.isdir(), "Links/devices are not allowed in deployment bundles")
            require(member.name in ("compose.yaml", "compose.deploy.yaml")
                    or member.name.startswith("infra/"), "Unexpected top-level bundle member")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("manifest")
    parser.add_argument("--approve")
    parser.add_argument("--compose")
    parser.add_argument("--bundle")
    args = parser.parse_args()
    try:
        data = load_manifest(args.manifest)
        if args.approve is not None:
            require(args.approve == data["commit"], "Approval SHA does not match the release manifest")
        if args.bundle:
            verify_bundle(args.bundle, data["bundle"]["sha256"])
        if args.compose:
            Path(args.compose).write_text(json.dumps({"services": {
                service: {"image": image} for service, image in data["images"].items()
            }}, indent=2) + "\n")
        print(f'Validated {data["release"]}: {data["commit"]}')
    except (KeyError, TypeError, ValueError, OSError, tarfile.TarError) as error:
        print(f"Release refused: {error}", file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    sys.exit(main())
