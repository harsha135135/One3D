# Release preparation verification

Started 2026-10-14; completed checks recorded through 2026-10-15.
Base commit: `940a6070e2bc2812c4cbba2687abc33d77f6a250` plus the release-preparation PR diff.
This records local preparation, **not an accepted, deployed or privately accessible release**.

## Independent remote observations

- PR 15 merged to the supplied SHA; candidate CI run
  [38418619854](https://github.com/harsha135135/One3D/actions/runs/38418619854) succeeded.
- Its deploy job logged: the instance was stopped, so deployment was skipped.
- Manual deployment [38419467403](https://github.com/harsha135135/One3D/actions/runs/38419467403)
  failed with the SSM command still `InProgress` in the final log. Runtime outcome unknown.
- The remote `git/matching-refs/tags/v1` query returned `[]` during inspection.
- AWS MCP `GetCallerIdentity` attempts returned `Unknown tool` before any AWS API ran.
  No current instance state, ECR digest, IAM policy, backup, private access or runtime
  health claim is based on those failed calls.

## Executed checks

| Check | Environment / result |
|---|---|
| Backend `./mvnw clean verify` | macOS, Java 21, disposable PostgreSQL/S3Mock/simulator Testcontainers: **265 passed**, comprising 74 unit and 191 integration tests; 0 failed/errored/skipped |
| Simulator `./mvnw clean verify` | Java 21: **14 passed**, 0 failed/errored/skipped |
| Negative controls before backend fix | New HTTP tests reproduced 13 failures; integration cases reproduced three erroneous reservation releases and one database diagnostic overflow |
| Frontend lint/build/typecheck | All passed using existing locked dependencies |
| Frontend Playwright | **26 passed** at desktop/phone sizes, with mocked API responses; initial sandbox localhost bind failed, rerun with local server permission passed |
| Static Coming Soon | Static checks and Chromium at 320/390/780/1024/1440 passed; see [browser results](../../public-coming-soon/review/browser-results.json) |
| Operations checks | **15 offline mocked regression tests passed** after review fixes, including inherited/concurrent lock behavior, database image/history guards and restore mismatch cleanup; shell syntax and workflow YAML checks passed |
| Terraform | Edited files formatted; isolated configuration validation passed with provider plugins allowed to run. No state-backed plan or apply executed |
| OneCreations integration | Separate checkout based on `9d3a2095`: typecheck/lint and **172 tests passed**; production build passed with webpack after default Turbopack workers hit an environment permission error. No original checkout changes |

Backend fixes add no database migration and change no simulator source/protocol. Invalid
or mismatched command reports now follow uncertainty handling; diagnostic text is bounded
to the database column in Unicode code points. The simulator keeps the full diagnostic.

The browser suite does not establish live Cognito, Razorpay or S3 behavior. Offline
operations tests do not establish successful AWS promotion or complete database recovery.
The static preview does not exercise a hosting provider's `_headers`/`_redirects`, public
DNS/HTTPS, social crawlers or full screen-reader conformance.

## Evidence correction

The CP9 restore log reports matching 15-table counts, a catalog-order chain and an exact
artifact checksum. Its custom request, submission revision and quote counts are zero.
The pasted description of a verified sample custom-order chain is not supported by that
log. A new isolated custom-chain restore remains an acceptance task.

## Still pending

- Current runtime SHA/image digests, health and old SSM command outcome.
- Live AWS IAM/retention inspection and an approved Terraform plan/apply.
- Candidate manifest populated from real retained image/bundle values.
- Frozen-release deployment/rollback and coordinated database/simulator/file restoration.
- Access/Tunnel setup, origin-bypass tests, revocation and real callback/webhook verification.
- Static hosting provisioning, final DNS record values, HTTPS and cutover approval.
- Release-policy scope mismatch resolution, Harsha's ownership reviews and acceptance.
- Release tag approval/publication and actual résumé update.

No AWS resource, DNS record, EBS data, live database, payment mode, printer, release tag
or main branch was changed during these preparation checks.
