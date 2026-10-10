# Evidence: Checkpoint 5: private STL request and manual quote

```text
Checkpoint / claim:  Roadmap Checkpoint 5. A customer uploads one STL model (at most 20 MB)
                     straight to private, versioned storage. The backend pins the exact stored
                     version, checks it and records its SHA-256. The customer then submits
                     requirements as an immutable revision. An operator reviews it, asks for
                     changes, rejects it or quotes it with a recipe. Accepting an unexpired quote
                     for the latest revision creates exactly one order whose scope names the
                     revision, file version and recipe.
Date:                2026-10-10
Code revision:       661f106 (background-work refactor, V7), 22baae8 (files), 4b01fed (requests,
                     quotes, custom orders), 93f334d (frontend, Compose storage), 9feb684 and
                     d39898e (test additions, header rule removed) on feat/checkpoint-5, on top
                     of main 5172578
Environment:         macOS (arm64); OpenJDK 21.0.12.1; Spring Boot 4.1.1; PostgreSQL 17.11;
                     AWS SDK for Java 2.55.13 (S3 client and presigner); adobe/s3mock 5.2 as
                     the local and test stand-in for S3; Next.js 16.4.0; Playwright 1.64.0
                     (Chromium); mock-oauth2-server 6.0.5
Implemented by:      the assistant, under the owner's instruction of 2026-10-09
Simulated:           S3 is S3Mock locally and in tests (real S3 arrives with AWS in Checkpoint 7).
                     The identity provider is the local mock. Payment is still the demo-only
                     simulation, and the printer is the simulator.
Owner review:        deferred by the owner until V1 is complete
```

## 1. Automated checks

| Suite | Command (from its directory) | Result |
|---|---|---|
| backend | `./mvnw clean verify` | **182 passed**: 34 unit, 148 integration (PostgreSQL, the simulator and S3Mock as containers). 22:38 IST on `d39898e`, 25.3 s |
| simulator | `./mvnw clean verify` | 14 passed (unchanged) |
| frontend | `npm run lint`, `npm run build`, `npm run typecheck`, `npm run test:e2e` | Lint and types clean. Build produced 12 routes. **20 Playwright runs passed**: 10 tests at 1280 × 800 and at 390 × 844 |

New backend tests:

| Class | Tests | What it covers |
|---|---|---|
| `StlCheckTest` | 8 | The checker: binary layout (including headers that start with "solid"), zero triangles, truncation, ASCII structure, a 300 KB ASCII model across read boundaries, and that a file over the limit stops after at most one extra read |
| `FilesIT` | 12 | Upload through the presigned URL the API returns, then finalize, check and download. Refusals before storage. The size check at finalize. Idempotent finalize. The upload window. Another customer. Version pinning after a second PUT to the same key. A late verifier. Operator retry |
| `CustomRequestsIT` | 7 | Submission, file ownership and single use, field lengths, request changes and a new revision, rejection, another customer, and the operator's exact-file download |
| `QuotesIT` | 20 | Accepted scope back to the file version. Repeat acceptance. **Two races, each repeated 5 times.** Expiry, withdrawal by a new revision, re-quoting, quotable states, another customer, a 200-character file name, a custom order through production to handoff, and acceptance re-checking the request |
| `CustomOrderConstraintsIT` | 14 | The V7 rules enforced by PostgreSQL with direct SQL (section 3) |

CI ([run 38070790632](https://github.com/harsha135135/One3D/actions/runs/38070790632) on `3038319`, PR #6) passed all three jobs:
- simulator: 14 tests;
- backend: 34 unit and 148 integration tests, in 2 min 34 s;
- frontend: lint, build, types and 20 Playwright runs.

The integration tests pulled `adobe/s3mock:5.2` on the GitHub runner.

Reports: [`backend-test-reports/`](2026-10-10-checkpoint-5/backend-test-reports/) and [`frontend/playwright-smoke.txt`](2026-10-10-checkpoint-5/frontend/playwright-smoke.txt).

## 2. Rules, enforcement and tests

| Rule | Enforced by | Proven by |
|---|---|---|
| The browser sends file bytes straight to private storage; the backend only signs a PUT for the declared size and a fixed `Content-Type` (15 minutes) | `FileService.startUpload`, `S3ObjectStorage.presignUpload` (Content-Length and Content-Type are signed) | `FilesIT` puts through the returned URL; the real stack uploads 20,971,484 bytes through `:3000/s3`; Playwright checks that the browser PUT carries no cookie or CSRF token |
| Oversized and non-STL uploads are refused before anything is stored | `FileService.startUpload` (1 to 20,971,520 bytes, `.stl` name), Bean Validation (name ≤ 200 characters), the frontend's own pre-check | `FilesIT.anOversizedOrNonStlUploadIsRefusedBeforeAnythingIsStored` (no row created); the real stack shows 400 `FILE_TOO_LARGE`; Playwright refuses a 20 MiB + 1 byte file |
| What was stored must match what was declared; the exact version is pinned at finalize | `FileService.finalizeUpload` (HEAD outside any transaction, size comparison, `versionId` required, row lock, work queued in the same transaction); V7 `artifact_version_pinned_after_upload` | `FilesIT.finalizeChecksWhatWasActuallyStored`, `finalizingTwiceQueuesOneCheckAndKeepsThePinnedVersion`; negative control N6 |
| Only checked files are used: the background check reads the pinned version (at most the limit plus one byte), computes SHA-256 and checks STL structure; it never renders or runs the file | `ArtifactVerifier`, `StlCheck`, `ArtifactVerification.apply` (claim token first) | `StlCheckTest`; `FilesIT` (binary, ASCII, HTML pretending to be STL); the real stack (an HTML "page.stl" is `REJECTED`); negative control N8 |
| A late verifier cannot overwrite the result of the one that replaced it | The shared work queue's claim token (Checkpoint 3), now in `background.PendingWorkStore` for both kinds of work | `FilesIT.aLateVerifierCannotOverwriteTheResultOfTheVerifierThatReplacedIt`; control B rerun after the move (section 4) |
| Files, requests and quotes are visible only to their owner; another customer gets 404, not 403 | Ownership in the query (`findByIdAndOwnerId`, `findByIdAndCustomerId`, `lockForAcceptance`) | `FilesIT.anotherCustomerCannotFinalizeReadOrDownloadTheFile`, `CustomRequestsIT.anotherCustomerCannotSeeOrReviseTheRequest`, `QuotesIT.anotherCustomerCannotSeeOrAcceptTheQuote`; real stack section 3; negative control N5. `SecurityIT` reads every new route from the handler mappings, so they are covered by its anonymous and customer route checks |
| Downloads are short-lived (5 minutes), for the exact checked version, and served as `application/octet-stream` attachments | `S3ObjectStorage.presignDownload` (versionId, response content type and disposition are signed); `DownloadRedirect` (`no-store`, `no-referrer`); signed URLs are never logged | `FilesIT.theDownloadIsTheExactCheckedVersionServedAsAnAttachment` (second PUT to the same key; download still returns the original bytes); `CustomRequestsIT.theOperatorDownloadsTheExactFileOfARevision`; real stack section 5; negative control N7 |
| A revision is never edited; a new file or new requirements are a new revision, reviewed from scratch, and an open quote is withdrawn in the same transaction | `SubmissionRevision` (`@Immutable`, no update path); `RequestService.revise`; `RevisionSubmitted` event with a synchronous `QuoteService.withdrawOnNewRevision` | `QuotesIT.replacingTheModelWithdrawsTheQuoteAndTheNewRevisionHasNoApproval`; real stack section 7; negative control N4a |
| A quote prices the latest revision only, with its own recipe revision; a request has at most one open quote | `RequestService.lockForQuote`; `RecipeService.createForQuote`; V7 `quote_one_issued_per_request`, `quote_revision_of_request_fk`, `recipe_revision_one_owner` | `QuotesIT.reQuotingWithdrawsTheEarlierQuote`, `onlyReviewableRequestsCanBeQuoted`; real stack (`REVISION_NOT_LATEST`); `CustomOrderConstraintsIT` |
| Acceptance is refused for an expired quote, a withdrawn quote, or a quote whose revision is no longer current | `Quote.requireAcceptable`; `RequestService.requireAwaitingAcceptance` (request `QUOTED`, revision latest); `CustomRequest.markOrdered` | `QuotesIT.anExpiredQuoteCannotBeAccepted`, `acceptanceChecksTheRequestItselfEvenIfAnOutdatedQuoteWereStillOpen`; real stack sections 7 and 11; negative controls N1, N4b |
| Acceptance creates exactly one order; repeating it returns the same order, including two acceptances at the same moment | `QuoteService.accept`: lock the request row, then the quote row; an `ACCEPTED` quote returns its order. V7 `order_line_quote_unique`, `quote_order_unique` | `QuotesIT.acceptingAgainReturnsTheSameOrder`, `twoSimultaneousAcceptancesCreateOneOrderAndBothSeeIt` (×5); real stack section 8; negative controls N2, N3 |
| Acceptance racing a new revision: exactly one wins cleanly | One lock order for every change: request row first, then quote | `QuotesIT.acceptanceRacingANewRevisionNeverLetsBothSucceed` (×5); negative control N3 |
| The accepted scope names the exact requirements, file version and recipe, and later uploads cannot change it | `order_line.quote_id` and `recipe_revision_id`; composite FK `order_line_recipe_of_quote_fk`; quote → revision → artifact `version_id` and `sha256` | `QuotesIT.acceptingAQuoteCreatesAnOrderForTheExactRevisionFileAndRecipe` (a second PUT to the same key changes nothing); real stack section 9 |
| Catalog and custom orders share production, and production uses the quote's recipe | `order_line.source`; V7 `order_line_source_matches_owner`; release reads `recipe_revision_id` for both | `QuotesIT.aCustomOrderGoesThroughProductionToReadyForHandoff`; real stack section 10; `CustomOrderConstraintsIT` |
| Snapshot values fit their columns | `QuoteService.itemName` (≤ 120 characters), `CUSTOM-` plus 8 characters (≤ 40), field limits matching columns | `QuotesIT.aLongFileNameIsShortenedToFitTheOrderLine`, `CustomRequestsIT.requirementsLongerThanTheirColumnsAreRefused` |

Design choices and limits for local storage are recorded in [decision 0001](../decisions/0001-local-file-storage-and-upload-checks.md).

## 3. Schema (V7)

[`V7__files_requests_quotes_custom_orders.sql`](../../backend/src/main/resources/db/migration/V7__files_requests_quotes_custom_orders.sql) adds the following:

- **New tables:** `artifact`, `custom_request`, `submission_revision` and `quote`.
- **`recipe_revision`:** now belongs to exactly one configuration or quote.
- **`order_line`:** gains `source` (`CATALOG` or `CUSTOM_QUOTE`) and `quote_id`. The snapshot columns keep their names. For custom lines they hold "Custom part: <file>", `CUSTOM-<quote>`, and the material and colour.
- **`pending_work`:** gains `VERIFY_ARTIFACT` work with `artifact_id`. A check ties each kind of work to its own reference.

V7 was applied to the existing local database with earlier orders in it. The backend log showed `Successfully applied 1 migration … now at version v7`, and the existing lines were backfilled as `CATALOG`. `CustomOrderConstraintsIT` writes rows directly with SQL to show that PostgreSQL refuses each of the following:
- a recipe with two owners or none;
- a custom line naming a configuration;
- a catalog line naming a quote;
- a custom line using another quote's recipe, or a catalog recipe;
- a second order for a quote;
- a quote for another request's revision;
- a second open quote;
- an accepted quote without an order;
- a file in two revisions;
- a finalized file without a pinned version;
- a verified file without a digest;
- work that names the wrong kind of record.

## 4. The background-work refactor did not weaken Checkpoint 3

`PendingWorkStore` moved from `production` to a new `background` package and now serves two kinds of work: start delivery and file checks. One scheduler runs every job. After the move:

- **All 121 Checkpoint 4 tests passed unchanged.**
- **Control B, claim token removed from both updates:** `DispatchReconciliationIT.aLateWorkerCannotWriteWhileTheReplacementWorkerHoldsTheClaim` failed. This is the same test that failed in the Checkpoint 3 control.
- **Control A3, printer lock, partial unique indexes and `@Version` all removed:** `ConcurrencyIT.twoSimultaneousStartsOnOnePrinterGiveExactlyOneAttempt` failed in **5 of 5** repetitions, as in Checkpoint 3.
  - The first A3 attempt did not mutate anything because of a shell-quoting mistake, and its tests passed. Only the second, verified-mutated run counts.

Outputs: [`CP3-B-claim-token-rerun.txt`](2026-10-10-checkpoint-5/negative-controls/CP3-B-claim-token-rerun.txt) and [`CP3-A3-all-concurrency-layers-rerun.txt`](2026-10-10-checkpoint-5/negative-controls/CP3-A3-all-concurrency-layers-rerun.txt).

## 5. Negative controls

| # | Removed or changed | Result | What it shows |
|---|---|---|---|
| N1 | Expiry check in `Quote.requireAcceptable` | `anExpiredQuoteCannotBeAccepted` failed: 200 instead of 409 | The expiry check is what refuses an expired quote |
| N2 | The "already accepted: return its order" branch | `acceptingAgainReturnsTheSameOrder` failed with 409 `QUOTE_NOT_CURRENT`; the simultaneous-acceptance race failed with [200, 409] in 5 of 5 | Repeat acceptance is idempotent because of this branch; without it no second order appears, but the second caller gets an error |
| N3 | The request-row lock (`@Lock` on `CustomRequestRepository.findForUpdate`) | `acceptanceRacingANewRevisionNeverLetsBothSucceed` failed in 5 of 5: the revision got 409 `CONCURRENT_MODIFICATION` | The lock gives a clean winner; without it `@Version` still prevents a double success but returns a generic conflict |
| N4a | The withdraw-on-new-revision listener | `replacingTheModel…` failed (quote stayed `ISSUED`); `acceptanceChecksTheRequestItself…` still passed | Withdrawal is the visible record; acceptance's own check still holds without it |
| N4b | Acceptance's request checks (status `QUOTED`, revision latest) | `acceptanceChecksTheRequestItself…` failed: `REQUEST_NOT_QUOTED` instead of `QUOTE_NOT_CURRENT` | The check bites; a third layer (`CustomRequest.markOrdered`) still refused and rolled back |
| N5 | File ownership in the query (`findByIdAndOwnerId` → `findById`) | `FilesIT.anotherCustomerCannotFinalizeReadOrDownloadTheFile` failed (200 instead of 404); `CustomRequestsIT.onlyTheCustomersOwnCheckedFileCanBeSubmittedOnce` failed (201 instead of 404) | Ownership in the query protects files and their use in requests |
| N6 | The stored-size comparison at finalize | `finalizeChecksWhatWasActuallyStored` failed: 200 instead of 409 | The application's own size check catches a mismatch that S3Mock accepts |
| N7 | `versionId` in the presigned download | Both download tests failed ("download link pins the checked version") | Downloads are pinned to the checked version only by this parameter |
| N8 | The final "not a valid STL" result in `StlCheck` | `StlCheckTest` (2 tests) and `FilesIT.contentThatIsNotAnStlModelIsRejected` failed (HTML became `VERIFIED`) | The structure check is what rejects non-STL content |

Each file was restored and checked with `cmp`. Outputs are in [`negative-controls/`](2026-10-10-checkpoint-5/negative-controls/).

## 6. Real stack: browser-like flow through the frontend

The stack for this run:
- `docker compose up -d db simulator idp storage storage-init`. The `storage-init` log shows "Bucket versioning enabled".
- The backend with the `demo` profile.
- The built frontend on `:3000`.

[`custom-part-flow.sh`](2026-10-10-checkpoint-5/real-stack/custom-part-flow.sh) uses curl cookie jars as three browsers. Every call goes through `http://localhost:3000`, including the presigned storage URLs under `/s3`. Signed URLs are kept in variables and never printed.

Transcript: [`custom-part-flow.txt`](2026-10-10-checkpoint-5/real-stack/custom-part-flow.txt).

| Step | Observed |
|---|---|
| A uploads the largest allowed model (20,971,484 bytes) to the presigned URL on `:3000/s3` | PUT 200; finalize pinned a version; the check gave `VERIFIED` with the SHA-256 of the local file |
| Refusals | 20,971,521 bytes: 400 `FILE_TOO_LARGE`. A `.obj` name: 400 `INVALID_FILE_NAME`. B finalizing, reading, downloading or submitting A's file: 404. An HTML file named `page.stl`: `REJECTED` |
| Operator downloads revision 1 through `:3000` | 302 to a link with the pinned `versionId`; the response is `application/octet-stream` and an attachment; the SHA-256 matches. A customer on that route: 403 |
| A replaces the model (revision 2) | Request back to `SUBMITTED`; the revision 1 quote `WITHDRAWN`; accepting it: 409 `QUOTE_WITHDRAWN`; quoting revision 1 again: 409 `REVISION_NOT_LATEST` |
| A accepts the revision 2 quote twice; B tries | B: 404 `QUOTE_NOT_FOUND`. A: one order (`CUSTOM_QUOTE`, "Custom part: bracket v2.stl", ₹1,599.00); the second acceptance returns the same id; one order line for the quote |
| A PUTs different bytes to the same key with the still-valid upload URL | Two versions are now stored for the key. The scope query is unchanged: order → quote → revision 2 (PETG, Orange, notes) → recipe (PETG, 200 µm) → file version and SHA-256. A's download is still the checked bytes |
| A pays (demo); the operator releases | The job's `recipeRevisionId` is the accepted quote's recipe |
| A quote moved into the past with SQL | Listed as `expired: true, acceptable: false`; accepting it: 409 `QUOTE_EXPIRED`; no order |

The backend and frontend logs from this run contain **no** signed URLs. A search for `X-Amz-Signature` and `X-Amz-Credential` found 0 lines.

After the run:
- **Order lines:** `SELECT source, count(*) FROM order_line GROUP BY source` returned `CATALOG | 19` and `CUSTOM_QUOTE | 4`. The earlier catalog orders were backfilled.
- **Storage restart:** `docker compose restart storage` (without `storage-init`) kept bucket versioning `Enabled` and both stored versions of the revision 2 key. A GET by the pinned `versionId` returned bytes whose SHA-256 matched the recorded one.

### Real browser

A Chromium session signed in through the mock provider's form, then used the real pages ([`browser-run.txt`](2026-10-10-checkpoint-5/screenshots/browser-run.txt)):
1. Choose a file on the Custom part page. The browser PUT it to `localhost:3000/s3/one3d-artifacts/uploads/<id>`, waited for the check and sent the request.
2. The operator saw it in Requests, quoted it in rupees on the review page, and the download link pointed at `/api/ops/requests/<id>/revisions/1/file`.
3. The customer accepted the quote and landed on the new order page.
4. There was no horizontal scrolling at 390 px.

Screenshots: [`screenshots/`](2026-10-10-checkpoint-5/screenshots/).

## 7. Found and fixed during this checkpoint

- **Quote acceptance read the quote before locking.**
  - **Cause:** accept loaded the quote to find its request, then locked the request and asked for the quote again `FOR UPDATE`. Hibernate returned the copy it had already read, so a second simultaneous acceptance saw `ISSUED`.
  - **What protected the data:** the quote's `@Version`. No second order was created, but the caller got 409 `CONCURRENT_MODIFICATION` instead of the same order.
  - **How it was found:** the race tests.
  - **Fix:** read only the request id before the lock (`QuoteRepository.findRequestIdById`).
- **A header rule that did nothing.** `next.config.ts` had `nosniff` and CSP headers for `/s3`. Next.js does not add configured headers to responses it proxies to another server, and a Next 16 `proxy.ts` did not either. Both were removed rather than kept as apparent protection. The protection that remains is the signed `application/octet-stream` and attachment response overrides, which S3Mock honours (section 6). On AWS, files are also served from S3's own origin.
- **Test messages and signed URLs.** A failing download assertion printed the presigned test URL. The checks are now boolean, so the message does not include the URL.

## Known limitations

- **S3Mock does not enforce signatures, expiry, the signed `Content-Length` or `Content-Type`.**
  - Locally, anyone who can reach `localhost:3000/s3` can write to the bucket. The local stack is for one developer.
  - The application's own checks are tested here: size at finalize, structure, version pinning and ownership.
  - **Owed in CP7:** that S3 itself refuses a wrong size, type, expired or tampered URL, and that the bucket is private, versioned and has the CORS rule for direct browser uploads, all on the real bucket.
- **The `aws` profile must replace every local storage default** in `application.yaml`: `endpoint` `127.0.0.1:9090`, `public-endpoint` `localhost:3000/s3`, the `local-dev-only` keys and path style.
  - **Owed in CP7:** the startup guard already owed for the mock identity provider must also refuse local storage settings under `aws`. Otherwise the deployed app would sign URLs with dummy keys pointing at localhost.
- **Test failure output can contain presigned URLs.**
  - When a MockMvc request in a failing test returns a redirect, Spring prints the response, including the `Location` header.
  - These URLs point at a throwaway test container, are signed with the dummy `local-dev-only` key and expire within minutes. The application itself never logs them.
- **Abandoned uploads.** Files left `UPLOAD_PENDING` after their 15-minute window are not removed by the application. The S3 lifecycle rule for `uploads/` (incomplete or noncurrent objects) is part of the CP7 Terraform. S3Mock keeps everything.
- **Finalize must happen within the upload window.** A very slow 20 MB upload that ends after 15 minutes must be uploaded again.
- **A file check whose tries run out leaves the file `VERIFYING`.** It is listed on the Operations page with a retry button (`POST /api/ops/files/{id}/retry-verification`).
- **The check is structural only.** It covers the binary layout or ASCII markers and the SHA-256. There is no geometry, manifold or printability analysis; preparation and slicing stay manual, outside the application, as V1 scope says.
- **File names keep only letters, digits, space, `.`, `-`, `_` and parentheses.** Other characters become `_`.
- **Review flow limits.**
  - Operators cannot ask for changes on, or reject, a request while a quote is open. They can re-quote, which withdraws the open quote.
  - Quotes are in INR only.
  - Expiry is checked at acceptance. Making the payment window fit the quote's validity is part of Checkpoint 6.
- **A download link is a bearer link for 5 minutes:** anyone holding it can fetch that one version. It is sent with `Cache-Control: no-store` and `Referrer-Policy: no-referrer`.
