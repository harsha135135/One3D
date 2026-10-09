# Evidence: Checkpoint 4: identity and bounded frontend

```text
Checkpoint / claim:  Roadmap Checkpoint 4. People sign in with OIDC. The backend enforces the
                     CUSTOMER/OPERATOR roles and order ownership on every route, and state
                     changes need a CSRF token. A small frontend gives customers and operators
                     usable pages with loading, empty and error states, keyboard access and a
                     390 px layout.
Date:                2026-10-09
Code revision:       b038390 (backend), 5e528cc (frontend), 9548341 (identity provider in
                     Compose, CI frontend job) on feat/checkpoint-4, on top of main e2f5d54
Environment:         macOS (arm64); OpenJDK 21.0.12.1; Spring Boot 4.1.1 / Spring Security 7.1.1;
                     PostgreSQL 17.11; Node 25.4 locally (CI uses Node 24 LTS from .nvmrc);
                     Next.js 16.4.0, React 19.3.0, TypeScript 5.9.3, ESLint 10.12.0,
                     Playwright 1.64.0 (Chromium); mock-oauth2-server 6.0.5 as the local
                     identity provider
Implemented by:      the assistant, under the owner's instruction of 2026-10-09
Simulated:           The identity provider is a local mock (Cognito arrives with the AWS
                     environment in Checkpoint 7). Payments are still the demo-only simulation.
                     The printer is the simulator.
Owner review:        deferred by the owner until V1 is complete
```

## 1. Automated checks

| Suite | Command (from its directory) | Result |
|---|---|---|
| backend | `./mvnw clean verify` | **121 passed**: 26 unit, 95 integration. 19:26:26 IST, 18.3 s |
| simulator | `./mvnw verify` | Unchanged since Checkpoint 3; run again in CI |
| frontend | `npm run lint`, `npm run build`, `npm run typecheck`, `npm run test:e2e` | Lint and types clean. Build produced 7 routes. **12 Playwright runs passed**: 6 tests at 1280 × 800 and at 390 × 844 |

New backend tests:

| Class | Tests | What it covers |
|---|---|---|
| `SecurityIT` | 9 | Integration tests; see section 2 |
| `CsrfCookieTest` | 2 | The real security configuration in a fresh web context: the first response sets a readable `XSRF-TOKEN` cookie, and an anonymous API call gets JSON 401 rather than a login redirect |
| `GroupRoleMapperTest` | 3 | Operator group membership gives `OPERATOR`, everyone else is `CUSTOMER`, and the groups claim name is configurable |
| `IdTokenIssuerTest` | 2 | An ID token from any other issuer is rejected |

CI ([run 37940394648](https://github.com/harsha135135/One3D/actions/runs/37940394648) on `9548341`) passed all three jobs: simulator, backend, and the new frontend job. The frontend job runs lint, build, type check and the Playwright smoke tests on Node 24.

All earlier integration tests now run as a signed-in operator with a CSRF token by default (`SignedInByDefault`). Every authorization check is therefore made explicitly in `SecurityIT`, which overrides that default per request.

Reports: [`backend-test-reports/`](2026-10-09-checkpoint-4/backend-test-reports/) and [`frontend/playwright-smoke.txt`](2026-10-09-checkpoint-4/frontend/playwright-smoke.txt).

## 2. Rules, enforcement and tests

| Rule | Enforced by | Proven by |
|---|---|---|
| Every `/api` route except `GET /api/catalog/**` needs sign-in; anonymous calls get JSON 401 `UNAUTHENTICATED` | `SecurityConfig` (`/api/**` authenticated), `SecurityProblems` entry point | `SecurityIT.everyApiRouteNeedsSignInExceptTheCatalog`, which reads **every** `/api` route from Spring's handler mappings, so a route added later is covered automatically; negative control N5 |
| Every `/api/ops/**` route needs OPERATOR; customers get 403 `ACCESS_DENIED`. `/ops` in the UI is only a page group | `SecurityConfig` (`hasRole("OPERATOR")`) | `SecurityIT.everyOperatorRouteIsForbiddenToCustomers` (all ops routes from the handler mappings); `operatorsCanUseTheOperatorRoutes`; negative control N1 |
| Customer A cannot read, list or pay for customer B's order, even knowing its id. Another customer's order is 404, so its existence isn't revealed | Ownership in the query: `OrderRepository.findOwnedWithLine(id, customerId)`, `findByCustomerId…` | `SecurityIT.aCustomerCannotReadListOrPayForAnotherCustomersOrder`, `aCustomerSeesOnlyTheirOwnOrdersNewestFirst`; real-stack section 3; negative control N2 |
| The owner comes from the server-side session, never from request data | `CurrentUser` reads the OIDC principal; `OrderController` passes `currentUser.id()` | `SecurityIT.theOwnerIsTakenFromTheSessionNotFromTheRequest` sends a `customerId` in the body, and the request still gets the session's id |
| State-changing requests need the CSRF token; a missing token gives 403 `CSRF_TOKEN_INVALID`, a different code from a role refusal | `csrf.spa()`; `CsrfCookieFilter` issues the cookie on the first response; `SecurityProblems` | `SecurityIT.aStateChangingRequestWithoutTheCsrfTokenIsRefused` (a user who is otherwise allowed); `CsrfCookieTest`; real-stack section 2; negative control N3 |
| The browser sends the CSRF token on every mutation | `frontend/src/lib/api.ts` (`X-XSRF-TOKEN` from the cookie) | Playwright keyboard test asserts the header on both mutations; negative control N4 |
| Users are identified by (issuer, subject); two first requests at once get one user without aborting a transaction | `app_user_identity_unique`; `UserDirectory` (`INSERT … ON CONFLICT DO NOTHING`, then read) | `ConcurrencyIT` (both racing requests are the same new user); every integration test creates users on first use |
| ID tokens must come from the configured issuer | `SecurityConfig.idTokenValidator` (`JwtIssuerValidator`) | `IdTokenIssuerTest` |
| Groups decide roles; role assignment stays with whoever manages the provider's groups | `GroupRoleMapper` | `GroupRoleMapperTest` |
| Sign-out ends the server session; the old session cookie stops working | Spring Security logout (session invalidated; 204) | Real-stack section 5: the **same** old cookie gives 200 before and 401 after |
| Every order has an owner; orders from before identity existed belong to a synthetic legacy user | V6: `customer_order.customer_id NOT NULL` and FK; backfill | `SchemaMigrationIT` (migrations 1–6); V6 applied to the existing local data (section 4) |
| UI states and accessibility: loading, empty and error states; keyboard use; no horizontal scrolling at 390 px; status shown as text, not colour alone | Pages in `frontend/src/app`, `States.tsx`, `globals.css` | Playwright: keyboard-only order and pay flow, no horizontal scroll on the catalog, order and ops pages at both widths, not-found and error alerts, operators-only guard |

## 3. Negative controls

| # | Removed or changed | Result | What it shows |
|---|---|---|---|
| N1 | `/api/ops/**` changed from `hasRole("OPERATOR")` to `authenticated()` | `everyOperatorRouteIsForbiddenToCustomers` failed (first: `GET /api/ops/jobs/{jobId}`) | The route matrix catches a missing role rule |
| N2 | `getOwnOrder` reads by id only, with no customer filter | `aCustomerCannotReadListOrPayForAnotherCustomersOrder` failed: 200 instead of 404 | Ownership in the query is what protects the order |
| N3 | CSRF disabled | `aStateChangingRequestWithoutTheCsrfTokenIsRefused` failed: 201 instead of 403 | The CSRF test bites |
| N4 | Frontend stops sending `X-XSRF-TOKEN` (rebuilt) | The keyboard order test failed in both viewports (header missing) | The browser test checks the header, not just the page |
| N5 | `/api/**` changed from `authenticated()` to `permitAll()` | `everyApiRouteNeedsSignInExceptTheCatalog` failed (`POST /api/orders` was reachable anonymously) | The anonymous matrix catches an open route |

Each file was restored and checked with `cmp`. Outputs are in [`negative-controls/`](2026-10-09-checkpoint-4/negative-controls/).

## 4. Real stack: browser-like sign-in through the frontend

The stack:
- `docker compose up -d db simulator idp`;
- the backend with `./mvnw spring-boot:run -Dspring-boot.run.profiles=demo`. Its log showed V6 applied to the existing local database: `Successfully applied 1 migration … v6`, with earlier orders assigned to the legacy user;
- the built frontend with `npm run start -- --port 3000`.

[`sign-in-and-security.sh`](2026-10-09-checkpoint-4/real-stack/sign-in-and-security.sh) uses curl cookie jars as three browsers. It goes through `http://localhost:3000`, where Next.js forwards `/api`, `/oauth2`, `/login` and `/logout` to the backend, and signs in through the mock provider's login form.

Transcript: [`sign-in-and-security.txt`](2026-10-09-checkpoint-4/real-stack/sign-in-and-security.txt).

| Step | Observed |
|---|---|
| Sign in as `customer-a`, `customer-b` and `operator-1` | The callback through `:3000` returns 302 to `http://localhost:3000/`. The session cookie is HttpOnly, and the XSRF-TOKEN cookie is present. `/api/me` shows the roles: CUSTOMER, or CUSTOMER and OPERATOR |
| A places an order with the CSRF header | `AWAITING_PAYMENT`, ₹499.00 |
| …without the header | 403 `CSRF_TOKEN_INVALID` |
| A reads the order | 200 |
| B reads the order, pays for it, and lists their own orders | 404 `ORDER_NOT_FOUND`; 404; A's order is not in B's list |
| B calls `/api/ops/orders` | 403 `ACCESS_DENIED` |
| A pays (demo); the operator's queue | `CONFIRMED` / `DEMO_SIMULATED`; the operator sees the order |
| A signs out | 204. The **same** old session cookie: 200 before, **401** after |

Screenshots of the real pages, signed in through the mock provider, are in [`screenshots/`](2026-10-09-checkpoint-4/screenshots/): the catalog, an order and my orders at desktop and 390 px, plus the operator queue and printers. They support the script; they are not the evidence.

## Known limitations

- **Mock identity provider locally.** Cognito (user pool, groups, MFA required for the demo pool) is created with the AWS environment in Checkpoint 7.
  - **Owed in CP7:** startup must fail under the `aws` profile if the mock provider's issuer or endpoints are configured, so the mock can never be used in the deployed demo.
- **Sessions live in server memory.** Restarting the backend signs everyone out. This is an accepted simplification for the single-instance demo.
- **The mocked Playwright tests** cover pages, keyboard use, layout and the CSRF header. The real cookie, CSRF and proxy path is covered by the real-stack script, which is not in CI yet.
- **No account management.** Users are created on first sign-in, and roles come from provider groups. There is no in-app user administration.
- **Local development uses `http://localhost`.** The session cookie becomes `Secure` with HTTPS in the `aws` profile (Checkpoint 7).
- **The demo payment button** appears only when the backend runs with the `demo` profile (`/api/me` reports `demoPaymentEnabled`).
