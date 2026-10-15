# Frozen V1 promotion interface

These scripts are **prepared locally, not deployed**. They do not establish the current runtime SHA, private access, image retention in AWS or owner acceptance. Follow the release runbook and obtain approval before running a promotion. GitHub CI publishes artifacts but no longer operates the recruiter environment. The permission reduction in `infra/terraform/demo/github.tf` must be applied and independently verified before the freeze is effective.

## Manifest

Keep the approved manifest and recovery inventory privately. Use real verified values; unresolved values must remain missing and validation must fail. The tag, if approved, names `commit`. `operations_commit` separately records the reviewed release tooling, so the candidate application is not rebuilt or silently retagged.

```json
{
  "schema_version": 1,
  "release": "v1.0.0",
  "commit": "<verified full application commit SHA>",
  "operations_commit": "<reviewed full operations commit SHA>",
  "target": {
    "account_id": "<12-digit account>",
    "region": "ap-south-1",
    "instance_id": "<verified existing instance ID>",
    "domain": "demo.one3d.store"
  },
  "bundle": {
    "bucket": "<existing one3d operations bucket>",
    "key": "deploy/<verified application commit>.tar.gz",
    "version_id": "<exact S3 VersionId>",
    "sha256": "<SHA-256 of that object version>"
  },
  "images": {
    "backend": "<account>.dkr.ecr.ap-south-1.amazonaws.com/one3d-backend@sha256:<digest>",
    "frontend": "<account>.dkr.ecr.ap-south-1.amazonaws.com/one3d-frontend@sha256:<digest>",
    "simulator": "<account>.dkr.ecr.ap-south-1.amazonaws.com/one3d-simulator@sha256:<digest>",
    "db": "postgres@sha256:<digest>",
    "nginx": "nginx@sha256:<digest>",
    "prometheus": "prom/prometheus@sha256:<digest>",
    "grafana": "grafana/grafana@sha256:<digest>",
    "certbot": "certbot/certbot@sha256:<digest>"
  },
  "database_versions": ["<versions read from the verified release database, in installed order>"]
}
```

The angle-bracket values above intentionally fail validation. Digests must be the deployed/retained images for the target x86 platform, not new builds. Preserve the exact image manifests/layers separately if registry availability cannot be guaranteed. Keep the migration checksums, SSM parameter names/configuration snapshot, private backup pointer/checksum, simulator journal backup, exact S3 artifact-version inventory, Terraform state version and dependency lockfiles in the accompanying recovery inventory. Never put credential values, database dumps, private models or Terraform state in Git.

Offline validation:

```sh
python3 infra/deploy/release_manifest.py /private/path/v1.0.0.json
```

## Promotion after approval

1. Verify the current host SHA, all three app image digests, service health, candidate CI, private access and backup/recovery inventory. Obtain owner acceptance and approval for the exact candidate and operations revision. No tag is created by these scripts.
2. Apply only the reviewed AWS permission/retention/protection changes after separate approval. Check that the GitHub role cannot send SSM commands, mutate IAM or write release configuration. Retaining workflow changes alone is insufficient.
3. Check out the manifest's committed `operations_commit`; the deployment directory must have no tracked modifications. Install the already-approved private-access overlay through its own runbook; this command does not make the demo private.
4. Ensure the target instance is already running. For the **first** transition, an owner-approved EC2 start may be needed: the new `demo.sh start` deliberately refuses an old installation without a release manifest. Running the historical startup script would select newest `main` and must be avoided.
5. Run the explicit promotion from an owner-authenticated terminal:

   ```sh
   AWS_PROFILE=one3d-deployer bash infra/deploy/remote-deploy.sh /private/path/v1.0.0.json --approve <exact application SHA>
   ```

The script checks the account, target state, main CI result, ECR SHA-tag-to-digest matches, exact S3 version and bundle checksum. It overlays only the reviewed `deploy.sh`, host-lock helper and manifest validator on the candidate bundle and adds digest-only Compose references. The resulting envelope is uploaded to the existing operations bucket with a content-derived key; the host checks the expected SHA-256 before extraction. This uses existing owner permissions and does not expand the instance role.

The host first compares the existing database container image with the approved digest, refusing a database image change before Compose can recreate it. It may start that same stopped container, then compares the ordered successful Flyway version history to the manifest before starting application services. This is a downgrade guard, **not proof of semantic migration compatibility or database integrity**; inspect checksums and schema separately. A new empty database must be restored/reviewed first. All eight images are pulled by digest. The successful-release marker advances only after the four healthchecked services are healthy, the proxy/monitoring processes run and the HTTPS catalog check passes.

The prior app bundle is preserved as `/opt/one3d/app.previous`. A subsequent promotion refuses to overwrite that directory. Archive it privately after reviewing the first promotion's result. A failed rollout leaves the old successful marker and the previous bundle available; it does not automatically revert a database or pretend the old application is still running.

A shared host lock serializes promotion, startup, shutdown preparation and restore. Children inherit the same lock descriptor; unique staging directories prevent cross-operation file reuse. Shutdown records the current boot ID to block a promotion between container stop and the EC2 stop request. A new boot clears that intent. If stopping EC2 fails, inspect the host and explicitly finish or cancel that shutdown intent before another operation. This is a single-host guard, not a distributed deployment system. After a private access cutover, the external `demo.sh status/start` HTTP result may be a gate response; it is not an application health assertion. The host check is the application check.

## Restart, rollback and recovery

- `infra/ops/demo.sh start` runs only the installed manifest matching the last successful marker. It never asks GitHub for `main` or upgrades a release. A missing or mismatched manifest requires explicit recovery/promotion.
- `infra/ops/demo.sh stop` refuses to stop EC2 if its PostgreSQL dump pipeline fails. That backup covers PostgreSQL; it does not by itself back up the simulator journal or every file version.
- `infra/ops/restore-check.sh` now fails on count, sample-order, file-record or checksum mismatches and removes its uniquely named temporary database on success/failure. It requires an idle demo and synthetic order/file fixtures. Run it on the live server only after approval; it creates a temporary database and writes a backup.
- A rollback is an explicit promotion of a reviewed manifest plus a compatible data state. If schemas differ, stop mutation/dispatch, restore into a separate database, reconcile the simulator journal and provider records, and verify exact model versions before switching. Restoring PostgreSQL alone does not rewind Razorpay or the simulator.
- `infra/ops/replace-instance.sh` refuses all modes. EC2/EBS replacement is outside this task and needs a separate approved recovery plan.

The original CP7 rollback evidence only exercises application/proxy configuration, not arbitrary migration reversal. Offline tests here exercise guard/control flow using mocked commands; they do not constitute an AWS rollout, full restore or live security test.
