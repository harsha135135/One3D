# Evidence: Checkpoint 6: payment sandbox and full journey

```text
Checkpoint / claim:  Roadmap Checkpoint 6. Customers pay through Razorpay in TEST MODE. An order
                     is confirmed only by a verified, captured payment of the right amount,
                     reported by a signed webhook or by the backend's own check with Razorpay's
                     API, and only once. Forged, duplicate, reordered, late, mismatched or
                     unknown reports never change an order; inconsistent ones are kept for an
                     operator. Payment never releases production. Catalog and custom orders
                     follow the same production workflow to handoff, and customers see a
                     timeline built from recorded facts.
Date:                2026-10-12
Code revision:       9ac75e9 (backend), 523489a (frontend), 9c9723d (quote acceptance near
                     expiry) on feat/checkpoint-6, on top of main 5aa0141
Environment:         macOS (arm64); OpenJDK 21.0.12.1; Spring Boot 4.1.1; PostgreSQL 17.11;
                     Razorpay test mode (Orders API, hosted Checkout, its test card and demo
                     bank page); Next.js 16.4.0; Playwright 1.64.0 (Chromium); mock-oauth2-server
                     6.0.5; S3Mock 5.2
Implemented by:      the assistant, under the owner's instruction of 2026-10-09
Simulated:           Razorpay is in test mode: no real money or bank. Webhooks cannot reach
                     localhost, so locally they are sanitized events signed with the configured
                     secret; real delivery from Razorpay is owed after the deploy (Checkpoint 7).
                     The identity provider is the local mock, storage is S3Mock and the printer
                     is the simulator.
Owner review:        deferred by the owner until V1 is complete
```

Design: [decision 0002](../decisions/0002-test-mode-payments.md).

## 1. Automated checks

| Suite | Command (from its directory) | Result |
|---|---|---|
| backend | `./mvnw clean verify` | **227 passed**: 46 unit, 181 integration. 11:18 IST on `9c9723d`, 28.0 s |
| simulator | `./mvnw clean verify` | 14 passed (unchanged) |
| frontend | `npm run lint`, `npm run build`, `npm run typecheck`, `npm run test:e2e` | Lint and types clean. Build produced 13 routes. **26 Playwright runs passed**: 13 tests at 1280 × 800 and at 390 × 844 |

**Every integration test now pays the real way.**
- The shared test context runs with Razorpay enabled. It uses dummy test-mode values and an in-memory provider (`FakeRazorpay`), and the demo shortcut is off.
- `ApiSteps.placeConfirmedOrder` opens a checkout and delivers a signed `payment.captured` webhook. So the production, quote and concurrency tests from Checkpoints 2 to 5 now confirm orders through the payment path.
- CI never calls Razorpay and needs no secrets.

New tests:

| Class | Tests | What it covers |
|---|---|---|
| `RazorpayWebhookIT` | 22 | See below |
| `CheckoutIT` | 7 | Exact total in test mode, reuse, provider outage (503, then a fresh checkout), checkout ends with the payment window, closed window (409), payment check confirms a captured payment and later webhooks are already recorded, an authorized payment does not confirm, a paid order is not checked again |
| `TimelineBackfillIT` | 1 | V8 on a database that already has orders: timelines are filled in, and a demo confirmation says "no payment was taken" |
| `PaymentModeGuardTest` | 6 | Test keys start; demo alone starts. Missing keys, a live key, demo together with Razorpay, and demo under `aws` all refuse to start |
| `WebhookSignatureTest` | 2 | Signatures computed independently in the test; any change to body, secret or signature fails |
| `RazorpayGatewayTest` | 4 | Against a local HTTP stub: Basic auth, `payment.capture = automatic`, response parsing, and an error message that does not echo the body |
| `SecurityIT` (+1) | 10 | The webhook is the only anonymous `/api` exception and still needs a signature; PUT and GET on that path are not open |
| `ProductionTransitionsIT` (+1) | | Handed over only after ready for handoff |
| `QuotesIT` (+1) | | A quote with less than 15 minutes left cannot be accepted, because its order could not be paid (`QUOTE_EXPIRING`) |

**`RazorpayWebhookIT` (22 tests):**
- **Applied correctly:** a signed captured payment confirms the order and creates no job or work.
- **Refused:** forged or tampered bodies, a missing signature, and a missing event id give 400 and record nothing.
- **Kept for review, order unchanged:** a wrong amount, a wrong currency, an unknown provider order, a second payment, and a late payment.
- **Ignored:** `payment.authorized` and `payment.failed`, including an authorized event that arrives after the capture.
- **Duplicates:** the same event twice, and `order.paid` after `payment.captured`, are both already recorded.
- **Races, each repeated 5 times:** two events for one payment, and two distinct payments.
- **Failure handling:** a failure while recording is not acknowledged, and the retry succeeds.
- **Operator review:** an operator resolves a review.

CI: see section 8. Reports: [`backend-test-reports/`](2026-10-12-checkpoint-6/backend-test-reports/), [`frontend/playwright-smoke.txt`](2026-10-12-checkpoint-6/frontend/playwright-smoke.txt).

## 2. Rules, enforcement and tests

| Rule | Enforced by | Proven by |
|---|---|---|
| Test mode only; the demo shortcut cannot bypass hosted checkout | `PaymentModeGuard` (startup) | `PaymentModeGuardTest`; real stack: the demo route is 404 with Razorpay on; controls P8, P9 |
| The server decides the amount; a checkout never outlasts the payment window (a custom order's window is its quote's validity) | `CheckoutService.open`, `CustomerOrder.requirePayable`, V8 `payment_window_ends_at` | `CheckoutIT`; `QuotesIT` (window equals quote expiry); real stack sections 2 and 6 |
| A quote cannot be accepted when too little time is left to pay for its order | `QuoteService.accept` (`MIN_TIME_TO_PAY`, 15 minutes) | `QuotesIT.aQuoteTooCloseToExpiryToBePaidCannotBeAccepted` |
| Razorpay is never called inside a database transaction | `CheckoutService` (insert, call, attach, each in its own transaction) | `CheckoutIT.aProviderOutageReturns503AndTheNextAttemptStartsAFreshCheckout` |
| A webhook is accepted only with a valid signature over the raw body and an event id; only POST on that one path skips the session and CSRF checks | `RazorpayWebhookController`, `WebhookSignature` (constant-time); `SecurityConfig` | `RazorpayWebhookIT.aForgedOrTamperedWebhookIsRefused…`, `SecurityIT`; real stack section 4; controls P1, P7 |
| Only a captured payment confirms; authorized or failed never does, from either path | `RazorpayWebhooks` (event type and status), `CheckoutService.check` (filter), `PaymentRecorder` (guard) | `authorizedOrFailedNeverConfirms…`, `CheckoutIT.aPaymentCheckDoesNotConfirmAnAuthorizedPayment`; real stack; control P2 |
| The amount and currency must match; otherwise the payment is kept for review | `PaymentRecorder.decide` | `aWrongAmountOrCurrencyIsKeptForReviewAndDoesNotConfirm`; real stack; control P3 |
| A browser success page is not evidence; the backend asks Razorpay itself | `CheckoutService.check` ignores browser input; the frontend calls `payment-check` | Playwright (the browser's payment id is not sent); real Checkout runs: `source = RECONCILIATION` |
| Event deduplication and payment deduplication are separate | V8 `payment_event_provider_event_unique`, `payment_provider_payment_unique`; `ON CONFLICT DO NOTHING` | `theSameEventDeliveredTwice…`, `orderPaidForAnAlreadyRecordedPayment…`, `CheckoutIT` (webhook after reconciliation); real stack section 4; controls P5, P6 |
| Concurrent reports for one order are decided one at a time; at most one payment confirms an order | Lock order then checkout (`OrderService.lockForPayment`); V8 `payment_one_applied_per_order`; `@Version` on the order | Two races, each repeated 5 times; control P4 |
| "Ambiguous" results go to manual review and never change the order: unknown provider order, amount or currency mismatch, late payment, second payment | `PaymentRecorder`, `ReviewReason`, operator `/api/ops/payments` | One test each; real stack sections 4 and 5 |
| A webhook is acknowledged only after its receipt and effect are committed | One transaction in `RazorpayWebhooks.apply`; controller answers after it returns | `aFailureWhileRecordingIsNotAcknowledgedAndTheRetrySucceeds` (500, no receipt kept; the retry applies) |
| Payment never releases production | No production call in the payment path; release stays an operator action | `aSignedCapturedPaymentConfirmsTheOrderButStartsNothing`, `ProductionWorkflowIT` (`jobId` null after payment); real stack section 3 |
| Handoff only after ready; customers see the steps | `CustomerOrder.markHandedOver`; `order_fact` written by orders, payments and production in their own transactions | `ProductionWorkflowIT` (full timeline), `QuotesIT` (custom), `ProductionTransitionsIT`, `TimelineBackfillIT`, Playwright; real stack section 8 |

## 3. Negative controls

| # | Removed or changed | Result | What it shows |
|---|---|---|---|
| P1 | Signature check always true | The forged-webhook test failed: 200 instead of 400 | The signature is what rejects a forged event |
| P2 | Captured-only checks in `PaymentRecorder` and the payment check | `aPaymentCheckDoesNotConfirmAnAuthorizedPayment` failed: the order became CONFIRMED | Authorized-only payments are kept out by these checks |
| P3 | Amount and currency comparison | The wrong-amount test failed: APPLIED instead of NEEDS_REVIEW | The amount check is what protects the order |
| P4 | The order-row lock | The distinct-payments race failed in 5 of 5. Both reports decided APPLIED, and `payment_one_applied_per_order` stopped the second with a 500 | The lock gives a clean decision; the partial unique index is the backstop (the 500 would make Razorpay retry, and the retry is then flagged for review). Two events for the same payment still passed, because payment-id deduplication covers them |
| P5 | `ON CONFLICT DO NOTHING` on the payment id | Both duplicate-payment tests failed: `payment_provider_payment_unique` raised a 500 | The constraint prevents double recording, but only the conflict clause lets the duplicate be acknowledged |
| P6 | `ON CONFLICT DO NOTHING` on the event id | The duplicate-event test failed: 500 from `payment_event_provider_event_unique` | The same, for event redelivery |
| P7 | CSRF exemption widened from the webhook to `/api/**` | `aStateChangingRequestWithoutTheCsrfTokenIsRefused` failed: 201 instead of 403 | The exemption is scoped to one route |
| P8 | Guard against demo plus Razorpay | `PaymentModeGuardTest.theDemoShortcutTogetherWithRazorpayDoesNotStart` failed | |
| P9 | Guard against non-test keys | `PaymentModeGuardTest.aLiveKeyDoesNotStart` failed | |

Each file was restored and checked with `cmp`. Outputs are in [`negative-controls/`](2026-10-12-checkpoint-6/negative-controls/).

## 4. Real stack with Razorpay test mode

**The stack:**
- `compose.yaml` services: database, identity provider, simulator and storage.
- The backend with `ONE3D_RAZORPAY_ENABLED=true`. The test keys came from the untracked `.env` as environment variables, and no `demo` profile was used.
- The built frontend on `:3000`.
- V8 was applied to the existing local database. The backfill gave the 16 earlier demo confirmations "Confirmed by the demo shortcut; no payment was taken".

**Simulator reset.** The local simulator's journal still had an externally started print on its bed, from the Checkpoint 3 refusal demonstration, which the backend has no record of. Only that one volume (`one3d-simulator`) was reset before the run. No pending or running work existed.

[`payment-journey.sh`](2026-10-12-checkpoint-6/real-stack/payment-journey.sh) drives the journey with curl cookie jars. It calls [`razorpay-test-payment.cjs`](2026-10-12-checkpoint-6/real-stack/razorpay-test-payment.cjs), which pays in Razorpay's **hosted Checkout** with Chromium:
- Razorpay's documented test card, Visa 4100 2800 0000 1007;
- a dummy mobile number;
- "Success" on Razorpay's demo bank page.

Transcript: [`payment-journey.txt`](2026-10-12-checkpoint-6/real-stack/payment-journey.txt).

| Step | Observed |
|---|---|
| Payment mode | `RAZORPAY_TEST`; the demo route is 404 |
| A opens a checkout for a catalog order | A real Razorpay test order (`order_Tmso…`, ₹499.00, INR, test mode). Opening again reuses it. B gets 404 for a checkout or a check on A's order. A check before paying asks Razorpay and finds nothing |
| A pays in hosted Checkout | The order page says "Payment received". The order is CONFIRMED (`RAZORPAY_TEST`). The payment `pay_Tmsp…` was recorded by the backend's own check with Razorpay (`RECONCILIATION`, APPLIED). **No production job** |
| Webhooks on the running server, through `:3000` | No signature, or a wrong secret: 400 `INVALID_SIGNATURE`. The same real payment reported by a webhook: ALREADY_RECORDED. The same event again: ALREADY_RECORDED. `payment.authorized`: IGNORED. A second distinct payment: NEEDS_REVIEW (`SECOND_PAYMENT`), with still exactly one APPLIED. Wrong amount on another order: NEEDS_REVIEW, and that order still awaits payment. Unknown Razorpay order: NEEDS_REVIEW. GET on the webhook path: 401 |
| Operator | Sees `UNKNOWN_PROVIDER_ORDER`, `AMOUNT_MISMATCH` and `SECOND_PAYMENT`, and resolves one with a note. A customer on that route: 403 |
| Custom part | Upload, request, quote (₹349.00, valid 3 days), accept. The order's "pay by" equals the quote's expiry. Paid in hosted Checkout: CONFIRMED (`RAZORPAY_TEST`) |
| Production | Both orders: release, start on the simulator (RUNNING), complete, QC passed, ready for handoff, handed over. The bed was cleared by the operator between the two prints |
| Timelines | Catalog: PLACED, CONFIRMED ("Paid with Razorpay test mode"), PAYMENT_UNDER_REVIEW ("An extra payment was received…"), RELEASED_TO_PRODUCTION, QC_PASSED, READY_FOR_HANDOFF, HANDED_OVER. Custom: the same, starting "From quote …" |

**README command.** The README's Razorpay start command was then run once exactly as written, in bash. It started the backend with Razorpay enabled, which rejected an unsigned webhook with `INVALID_SIGNATURE`, and it was stopped.

**Secret checks.**
- The values of `RAZORPAY_KEY_ID`, `RAZORPAY_KEY_SECRET` and `RAZORPAY_WEBHOOK_SECRET` were searched for with `grep -cF`, which prints counts only.
- **0** occurrences in the backend and frontend logs, under `docs/` and in tracked files.
- The backend log has no signature, HMAC or contact details.

Screenshots of both Checkout runs are in [`screenshots/catalog`](2026-10-12-checkpoint-6/screenshots/catalog/) and [`screenshots/custom`](2026-10-12-checkpoint-6/screenshots/custom/). Each run has:
- the order page;
- the test card in hosted Checkout;
- Razorpay's demo bank page;
- the confirmed order with its timeline.

## 5. Notes from implementation

- **The order is never read before its lock.** The Checkpoint 5 stale-read lesson applied here: the payment path finds the order through a checkout projection and locks it before reading it. `CustomerOrder` also gained a `@Version`.
- **Razorpay's current hosted Checkout:**
  - it asks for a mobile number before card payment;
  - in test mode the "bank page" is a Success or Failure choice, not an OTP form.
  
  The automation needed two exploratory runs to find this; both are recorded in the transcript's steps.

## 6. Owed in Checkpoint 7

- **Real webhook delivery.**
  - The owner configures the dashboard webhook: URL `https://demo.one3d.store/api/payments/razorpay/webhook`, the same secret, and the events `payment.captured`, `order.paid`, `payment.authorized` and `payment.failed`.
  - The check must show a real `payment.captured` delivery answered **APPLIED or ALREADY_RECORDED**, not just any 2xx. A capture event whose payment entity could not be read is acknowledged as IGNORED and leaves only a log line.
  - It must also show that the `X-Razorpay-Event-Id` header is present. Without it the endpoint answers 400, and Razorpay would retry for 24 hours and then disable the webhook.
  - The risk is low: the same parser already read real Razorpay payment entities from the API in both reconciliation runs here.
- **Nginx:**
  - its Content-Security-Policy must allow scripts and frames from `checkout.razorpay.com` (and `api.razorpay.com`);
  - it must keep the request-body limit small for `/api`. The webhook is open without sign-in and its body is read before the signature check. File uploads go straight to S3, so there is no reason to raise the limit.
- **The `aws` profile** reads the keys from SSM. The guard already refuses non-test keys and the demo shortcut.

## 7. Local data reset for this run

The local simulator journal (Docker volume `one3d-simulator`) was deleted and recreated before the real-stack run.
- **Why:** it still held an externally started print from the Checkpoint 3 refusal demonstration. Only a start command naming that print could clear it, and the backend has no record of it.
- **What else was checked:** nothing else was removed. The backend had no pending or running work at the time.

## 8. CI

[Run 38209645598](https://github.com/harsha135135/One3D/actions/runs/38209645598) on `2fd93da` (PR #7) passed all three jobs:
- **simulator:** 14 tests;
- **backend:** 46 unit and 181 integration tests in 2 min 24 s, with PostgreSQL, the simulator and S3Mock as containers and no Razorpay secrets;
- **frontend:** lint, build, types and 26 Playwright runs.

## Known limitations

- **No refunds or cancellation in V1.** Resolving a review records a decision; any refund is done in the Razorpay dashboard. An unpaid order stays AWAITING_PAYMENT after its payment window closes, and no new checkout can be opened.
- **Payment checks are not rate-limited per customer.** Each call asks Razorpay about at most 5 of the order's checkouts, and only while the order awaits payment.
- **The customer timeline shows the Razorpay payment id.** That id is a test-mode identifier, not a secret.
- **Local webhooks in this record are replays.** They are sanitized events signed with the configured secret, exactly as Razorpay signs them. Razorpay itself has not yet delivered one; that is the Checkpoint 7 item above.
