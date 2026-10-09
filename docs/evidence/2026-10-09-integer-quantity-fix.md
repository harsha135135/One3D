# Evidence: reject fractional order quantities

```text
Claim:               POST /api/orders rejects a non-integer quantity (1.5, 0.5, 1.0, 1e0)
                     with 400 and stores nothing. It used to truncate 1.5, 1.0 and 1e0 to 1.
Date:                2026-10-09
Code revision:       b8e19cc (branch fix/integer-quantity, on top of main 055b73e)
Environment:         OpenJDK 21.0.12.1; Maven 3.9.13 via ./mvnw; Spring Boot 4.1.1 (Jackson 3.1.5);
                     Testcontainers 2.0.5; PostgreSQL 17.11
Implemented by:      the assistant, under the owner's instruction of 2026-10-09 (see IMPLEMENTED.md)
Owner review:        deferred by the owner until V1 is complete
```

## Problem

Jackson coerces a JSON floating-point number into an integer field by default, dropping the fraction. With quantity defaulting to 1 and limited to 1 by `@Min`/`@Max`:
- `{"quantity": 1.5}`, `1.0` and `1e0` became 1 and were accepted with **201**;
- `0.5` became 0 and was rejected only by `@Min`.

The new regression test reproduced this before the fix: three of its four cases failed with "Status expected:<400> but was:<201>".

## Fix

- `application.yaml` sets `spring.jackson.deserialization.accept-float-as-int: false`. A floating-point JSON number can no longer fill an integer field. Integers must be sent as JSON integers.
- `ApiExceptionHandler` reports well-formed JSON with a wrong-type value as `400 VALIDATION_FAILED` with the field name in `errors`. Only unreadable JSON still returns `MALFORMED_REQUEST`.
  - Side effect: `POST /api/ops/attempts/{id}/qc` with `{"result": "MAYBE"}` now returns `VALIDATION_FAILED` with field `result`, instead of `MALFORMED_REQUEST`. `ProductionTransitionsIT.malformedOperatorRequestsAreRejected` was updated to match.

## Tests

| Check | Result |
|---|---|
| `OrderApiIT.nonIntegerQuantityIsRejectedAndNothingIsStored` (parameterized: `1.5`, `0.5`, `1.0`, `1e0`) | Each returns 400 `VALIDATION_FAILED` with `errors[0].field = quantity`, and the `customer_order` count is unchanged |
| `./mvnw clean verify` in `backend/` on the fix (18:21:59 IST) | `BUILD SUCCESS`, **73 passed** (15 unit, 58 integration), 0 failed. That's the previous 69 plus 4 parameterized cases. Reports: [`2026-10-09-integer-quantity-fix/`](2026-10-09-integer-quantity-fix/) |
| Negative control: `accept-float-as-int: true` set temporarily, `OrderApiIT` run | `BUILD FAILURE`: the `1.5`, `1.0` and `1e0` cases returned 201. The file was restored and checked with `cmp` |

## Limits

- Quoted numbers such as `"1"` are still coerced to 1 by Jackson's default scalar coercion. That is not truncation, so it is left unchanged.
