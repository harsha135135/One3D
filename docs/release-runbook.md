# V1 freeze, promotion and recovery runbook

Prepared 2026-10-14; reviewed 2026-10-15. **No release tag, cloud change or live
cutover has been performed.** These are approval-gated procedures, not a completion record.
See [assessment](release-review.md) and [verification](evidence/2026-10-14-release-preparation.md).

## 1. Resolve the candidate before freezing anything

The supplied candidate is `940a6070e2bc2812c4cbba2687abc33d77f6a250`, the merge of
[PR 15](https://github.com/harsha135135/One3D/pull/15).

- [CI 38418619854](https://github.com/harsha135135/One3D/actions/runs/38418619854)
  passed its tests/image jobs. The deployment step explicitly skipped because EC2 was stopped.
- [Manual deployment 38419467403](https://github.com/harsha135135/One3D/actions/runs/38419467403)
  failed after polling SSM command `2f4bba88-66fc-4895-90e0-813e89b0104b`, still `InProgress`
  in that log. The action's failure does not prove the remote command stopped.
- No `v1*` tag was returned by the remote tag query during this review. Recheck before tagging.
- The AWS connector repeatedly returned `Unknown tool` before an API executed. Current
  account, instance state, command outcome, service health and deployed digests remain unverified.

**Next read-only inspection**, in AWS Console or a restored authorized AWS connection:

1. Select the owner's account and Mumbai (`ap-south-1`). Check the existing instance
   `i-026fb29f6b8d6591a` and SSM Run Command result above. Do not retry deployment while
   a command may still be executing. If stopped, request an explicit start for verification;
   do not run the historical `demo.sh start`, which upgrades to latest passing main.
2. Through SSM Session Manager, read `/opt/one3d/deployed-tag`, inspect containers and
   their configured image references, image IDs and repository digests, and query health.
   Example read-only host commands:

   ```sh
   cat /opt/one3d/deployed-tag
   docker ps -a --format '{{.Names}} {{.Image}} {{.Status}}'
   docker inspect --format '{{.Name}} {{.Config.Image}} {{.Image}} {{json .State.Health}}' one3d-backend-1 one3d-frontend-1 one3d-simulator-1
   curl --fail --resolve demo.one3d.store:443:127.0.0.1 https://demo.one3d.store/api/catalog/configurations
   ```

   Verify actual container names first. Do not print full Docker inspect/environment
   output or SSM secret values. Read each image's `RepoDigests` and match the ECR manifest
   digest for the candidate and platform. The marker alone is insufficient: the old
   deploy script wrote it before health checks.
3. Record the deployed revision and all mismatches. Preserve the existing data and logs.
   An HTTP 200 proves only that an endpoint answered. Do not infer owner acceptance.

The new simulator fixes change application code. If included, use a **new** full commit
with passing CI and a separately verified deployment. Do not tag `940a607` and describe
it as containing fixes that were made later.

## 2. Apply release protections only after approval

Review the feature PR, then approve its merge separately. CI remains tests/build/publish;
the GitHub role must lose SSM command permissions in AWS before future workflow edits
can no longer operate the demo. Preserve the owner-operated promotion path.

Prepare a Terraform plan using the existing state/backend and account. Keep the plan
private because state/plans can contain secrets. Inspect every change; expected changes
are IAM permission reduction, retention settings and deletion protection. **Reject any
instance replacement, EBS attachment/data change, resource destruction, or unexpected
new resource.** Validation is not a live plan. No apply is authorized by this document.

The proposed ECR rule expires untagged images only. Release/SHA-tagged images remain;
do not remove their protecting tags. The operations bucket keeps deploy object versions.
This increases retained storage: review costs and explicitly prune only artifacts not
referenced by retained manifests. Terraform `prevent_destroy` protects operations that
respect that configuration; it is not an IAM deny against console deletion.

After an approved apply, independently inspect effective CI permissions and retention.
Check other attached policies too: removing one statement is insufficient if another
policy still permits SSM, IAM mutation or another path to change the instance.

## 3. Capture the release and recovery inventory

Use the exact [manifest schema](../infra/deploy/README.md). Keep completed manifests
and backup inventories in owner-controlled private storage, with a second protected copy.
The checked-in example intentionally has invalid placeholders; no digests are invented.

| Preserve | Why |
|---|---|
| Application SHA, operations SHA and CI URLs/results | Source identity and the exact deployment code are separate facts |
| Eight image digests, platform, original tags and retained OCI manifests/layers | Source tags and rebuilds alone cannot restore disappeared images |
| Deployment bundle S3 key, VersionId and SHA-256 | A key can be overwritten even when the filename contains a commit |
| Flyway installed order, versions, checksums, success flags and migration files | Image rollback cannot undo an incompatible schema |
| Nonsecret configuration values; secret parameter names and rotation/recovery instructions | Recreate configuration without putting secrets into Git |
| Private PostgreSQL dump, object VersionId, checksum and timestamp | Recover persisted business records |
| Consistent simulator SQLite backup/journal and checksum | Restoring only PostgreSQL can lose execution knowledge |
| Exact artifact key/version/digest inventory and retained bytes | Database records alone do not recover reviewed models |
| Terraform lockfile, backend/key and private state version; resource/volume identifiers | Recover the infrastructure mapping without committing state |
| Access policy, tunnel config, DNS zone snapshot and hosting deployment ID | Recover entry points independently of application source |
| Synthetic catalog/custom fixtures, demo recording and test reports | Repeat the interview and recovery checks after logs expire |

Quiesce customer changes and dispatch before a coordinated release backup. Use a
SQLite-consistent backup or stop the simulator cleanly; do not copy only a live SQLite
main file while ignoring its WAL. Keep DB, simulator and file inventories from the same
maintenance window. Protect backups as private data; do not place them in PR attachments.

## 4. Rehearse deployment and rollback

First validate offline:

```sh
python3 infra/deploy/release_manifest.py /private/path/v1.0.0.json
python3 -m unittest discover -s infra/ops/tests -v
```

After explicit approval of target instance, application/operations SHAs and manifest:

```sh
AWS_PROFILE=one3d-deployer bash infra/deploy/remote-deploy.sh /private/path/v1.0.0.json --approve <exact-application-SHA>
```

The script refuses unresolved pins, the wrong account, a stopped target, absent passing
CI or a digest mismatch. Its deployment envelope uses the candidate configuration with
reviewed operations scripts and digest-only Compose overrides. Read the script README
for first-transition and database-image safeguards. An empty database requires an
explicit isolated restoration; promotion is not database provisioning.

**Same-schema rollback:** preserve the failed rollout's evidence, inspect actual running
images and use the earlier approved manifest only after compatibility review. Preserve
`app.previous` privately before allowing a later promotion; do not overwrite the only
known prior bundle. A failed health check does not prove the previous stack is still running.

**Changed-schema rollback:** do not run old images against a newer database, reverse SQL
blindly or restore over the live volume. Stop writes/dispatch; restore the matching backup
into a separate disposable database; verify migrations, constraints, both order chains,
files and simulator state. Reconcile provider payments and simulator commands that occurred
after the backup. Obtain explicit approval for switching the restored environment into
use and acknowledge the recovery window. Database restore cannot rewind external effects.

The revised restore checker verifies its sampled counts/records/file and migration history
and fails on mismatches. It does not prove every row or a custom chain if the fixture lacks
one. Add and explicitly verify a synthetic custom request → revision → quote → order →
payment → attempt/QC chain alongside a catalog order. Do this in isolation before a live
maintenance rehearsal. Record the exact revisions, commands and observed results.

Existing CP7 evidence tests application/proxy rollback. The new mocked regression tests
test guards. **A complete frozen-release rollback on AWS remains unexecuted.** Do not
mark this acceptance gate passed until the approved rehearsal succeeds.

## 5. Privacy and public-site cutover

Use [private-demo.md](private-demo.md) for Access/Tunnel, callback, webhook and origin
tests. The current promotion script retains the existing HTTPS/HTTP-01 route; private
tunnel termination needs a separately reviewed operations variant. Do not close ports
and assume that renewal/startup will continue unchanged.

Deploy only `public-coming-soon/dist` to the chosen static host after approval. Verify
its assigned URL, then complete the Hostinger/Cloudflare record plan with **actual**
assigned hostnames. Export the zone before DNS changes. The detailed public-site
[README](../public-coming-soon/README.md) includes records and rollback. Publish the
OneCreations handoff change only after the public apex works. No public link targets the
recruiter demo.

## 6. Acceptance, tag and future work

1. All required scope checks pass, including the unresolved release-policy mismatch
   described in the assessment. Record any explicitly accepted scope correction.
2. Harsha completes the [ownership exercises](interview-defense.md), rehearses the demo,
   accepts the exact revision and reviews the final recovery/private-access evidence.
3. Recheck tag absence and obtain approval for the exact SHA. Only then create an annotated
   `v1.0.0` tag, push that tag and publish truthful release notes. Do not move an existing tag.
4. Update the actual résumé using evidenced claims; keep this distinct from publishing a tag.
5. Begin only an explicitly agreed V2 increment. V2 uses different databases/volumes and
   no V1 demo credentials. CI publishing must never imply deployment promotion.

No step above authorizes EC2 replacement, EBS deletion/modification, Terraform destruction,
live payments, physical printer operation or an unexpected paid resource.
