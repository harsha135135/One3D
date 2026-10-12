# 0002: Test-mode payments with Razorpay

Date: 2026-10-12 (Checkpoint 6). Status: accepted by the assistant under the owner's instruction of 2026-10-09; owner review deferred.

## Context

V1 takes payment through one provider in test mode ([v1-scope.md](../v1-scope.md#payment-in-test-mode)). A browser's "success" is not evidence. Duplicate, reordered or inconsistent provider reports must not confirm an order twice or release production. Razorpay delivers webhooks at least once, possibly out of order. It retries any non-2xx answer for 24 hours, and it waits only 5 seconds for an answer. Webhooks cannot reach a developer's machine.

## Decision

- **Test mode only.** At startup the application refuses each of these:
  - Razorpay key ids that are not test-mode (`rzp_test_…`);
  - Razorpay enabled without all three keys;
  - the demo payment shortcut together with Razorpay;
  - the demo payment shortcut under the `aws` profile.
- **The server decides the amount.**
  - A checkout creates a Razorpay order for exactly the order total, with automatic capture.
  - A checkout row is written before Razorpay is called and gets Razorpay's order id afterwards. Razorpay is never called inside a database transaction.
  - A checkout never outlasts the order's payment window. For a custom order, that window ends when its accepted quote would have expired.
- **Only captured payments confirm.** An order is confirmed only by a payment whose status is `captured`, of the checkout's exact amount and currency, started before the checkout closed. That payment is reported in one of two ways:
  - **A signed webhook:** HMAC-SHA256 of the raw body with the webhook secret. Only `payment.captured` and `order.paid` events can confirm. `payment.authorized` and `payment.failed` are recorded and change nothing.
  - **A server-side check of Razorpay's API** (`GET /v1/orders/{id}/payments`). The browser triggers it after Checkout closes, but what the browser reports is not used.
- **Two kinds of deduplication.**
  - **Events:** a unique provider event id makes a redelivered event a no-op.
  - **Payments:** a unique provider payment id makes the same payment reported twice a no-op. That covers two different events, or a webhook plus a reconciliation.
  - Both inserts use `ON CONFLICT DO NOTHING`, so a duplicate waits for the first transaction and is then acknowledged.
- **One lock order.** Lock the order row, then the checkout row, then decide. Concurrent reports for one order are therefore decided one at a time. A partial unique index allows only one APPLIED payment per order, as the backstop.
- **Acknowledge only what is durable.** The webhook receipt and its effect commit in one transaction, and the endpoint answers 2xx only after that commit. Any failure answers 5xx and leaves nothing, so Razorpay retries.
- **Manual review instead of guessing.** A captured payment is kept as NEEDS_REVIEW, and the order does not change, when:
  - its Razorpay order is unknown;
  - its amount or currency differs;
  - it started after its checkout closed;
  - the order was already paid.
  
  Operators see these and record what they decided. Refunds are done in the Razorpay dashboard and are out of V1 scope.
- **Payment never releases production.** An operator releases a paid order explicitly.
- **One exempt route.** Only `POST /api/payments/razorpay/webhook` skips the session and CSRF checks, and it requires the signature instead.

## Consequences

- **Payloads are not stored.** Razorpay payloads contain customer contact details, so only identifiers, amounts and outcomes are kept. Bodies, signatures and keys are never logged.
- **Locally, the API check stands in for webhooks.** Locally verified webhook handling uses events signed with the configured secret. Real delivery from Razorpay to `https://demo.one3d.store/api/payments/razorpay/webhook` is set up and checked after the AWS deploy (Checkpoint 7). That needs the dashboard webhook (URL, secret, events `payment.captured`, `order.paid`, `payment.authorized`, `payment.failed`) and a Content-Security-Policy that allows `checkout.razorpay.com`.
- **An unpaid order stays AWAITING_PAYMENT after its window closes.** No new checkout can be opened, and cancellation is out of V1 scope.
