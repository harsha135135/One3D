# 0001: Local file storage and upload checks

Date: 2026-10-10 (Checkpoint 5). Status: accepted by the assistant under the owner's instruction of 2026-10-09; owner review deferred.

## Context

V1 stores customer STL files in a private, versioned S3 bucket ([ARCHITECTURE.md](../../ARCHITECTURE.md#immutable-direct-uploads)). The browser uploads directly to storage and the backend pins the exact version. Local development and the integration tests need an S3-compatible stand-in that supports bucket versioning and reads by `versionId`.

Options checked on 2026-10-09 and 2026-10-10:

- **LocalStack:** now needs an auth token even for local use. Rejected.
- **MinIO:** the server images used before are no longer published. Rejected.
- **S3Mock** (`adobe/s3mock:5.2`): checked with the AWS SDK. Versioned PUT returns a `VersionId`, HEAD returns the latest version, and GET by `versionId` returns the old bytes. Presigned GET honours the `response-content-type` and `response-content-disposition` overrides. Chosen.

## Decision

- **Storage interface.** The backend uses one storage interface (`files.ObjectStorage`) with an AWS SDK v2 adapter.
  - Locally and in tests, the adapter points at S3Mock with path-style addressing and a dummy key.
  - On AWS (Checkpoint 7), the endpoints and keys are empty and the SDK uses S3 with the instance role.
- **The application never creates or configures the bucket.**
  - Locally, `compose.yaml` creates it, and its `storage-init` step turns on versioning.
  - In tests, `StorageRig` does the same.
  - On AWS, Terraform does it.
  - If storage is not versioned, finalize refuses with 503 `STORAGE_NOT_VERSIONED` instead of pinning nothing.
- **Same-origin storage URLs locally.** Presigned URLs use a browser-facing endpoint. Locally that is `http://localhost:3000/s3`, which Next.js forwards to S3Mock, so uploads and downloads need no CORS. On AWS the browser talks to S3 directly, which needs a CORS rule on the bucket (Terraform, Checkpoint 7).
- **Upload before request.** A file is uploaded and checked first, then named in a request revision. One file can belong to one revision only.
- **Upload keys.** Every upload gets a random key under `uploads/`.
- **Abandoned uploads:**
  - Files never finalized stay `UPLOAD_PENDING` after their 15-minute window and are never used.
  - Their objects, and noncurrent versions that no revision references, are removed by an S3 lifecycle rule on `uploads/`. That rule is part of the Checkpoint 7 Terraform.
  - Locally, S3Mock keeps everything.

## Consequences

- **What S3Mock does not enforce.** It does not check signatures, expiry, or the signed `Content-Length` and `Content-Type`.
  - The application's own checks are tested here: stored size at finalize, STL structure and SHA-256 on the pinned version, ownership, and version-pinned downloads.
  - S3's enforcement must be shown against the real bucket in Checkpoint 7.
  - On the local stack, anyone who can reach `localhost:3000/s3` can write to the bucket. The local stack is for one developer.
- **Next.js does not add configured response headers to responses it proxies to another server.** The protection against a stored file being rendered on the app's origin is the signed `application/octet-stream` and attachment response overrides. On AWS, files are also served from S3's own origin.
- **The `aws` profile must replace every local storage default:** endpoint, public endpoint, dummy keys and path style. The startup guard owed in Checkpoint 7 for the mock identity provider must also refuse local storage settings under `aws`.
