# Evidence: Checkpoint 1: API, database and catalog order

```text
Checkpoint / claim:  Roadmap Checkpoint 1. A Spring Boot API on PostgreSQL creates and
                     retrieves a one-line catalog order whose line snapshots the
                     catalog values; schema built only by Flyway migrations.
Date:                2026-10-07
Code revision:       0fb442efcab567c37fb1dec2c431fb8a48b88282 (branch feat/checkpoint-1)
Environment:         macOS 26.5.2 (arm64); OpenJDK 21.0.12.1 (Homebrew); Maven 3.9.13 via
                     ./mvnw; Spring Boot 4.1.1; Docker client 28.5.0 / engine 29.6.2;
                     Testcontainers 2.0.5; PostgreSQL 17.11 (postgres:17-alpine) for
                     both tests and the local database
What is simulated:   Synthetic catalog data only. No payment, printer, customer
                     identity or authentication exists at this checkpoint.
Owner explanation/change/debug review: PENDING (see the end of this record)
Related IMPLEMENTED.md rows: Java modules/schema; catalog product; integration tests;
                     runnable setup/API documentation (all partial)
```

## 1. Automated checks

**Purpose:** confirm that the rules hold against real PostgreSQL, and that every test class actually runs under `./mvnw verify`.

**Command** (from `backend/`, Docker running):

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./mvnw clean verify
```

**Actual result:** `BUILD SUCCESS`, exit code 0, total time 7.933 s. It finished at 2026-10-07T13:38:09+05:30 on the committed revision above.

| Plugin / phase | Class | Tests | Result |
|---|---|---|---|
| Surefire (`test`) | `orders.CustomerOrderTest` (unit, no Spring/DB) | 3 | 3 passed |
| Failsafe (`integration-test`) | `DatabaseConstraintsIT` | 7 | 7 passed |
| Failsafe | `catalog.CatalogApiIT` | 2 | 2 passed |
| Failsafe | `SchemaMigrationIT` | 2 | 2 passed |
| Failsafe | `orders.OrderApiIT` | 9 | 9 passed |
| Failsafe | `orders.OrderSnapshotIT` | 2 | 2 passed |
| Failsafe | `orders.OrderAtomicityIT` | 2 | 2 passed |
| **Total** | | **27** | **27 passed, 0 failed, 0 skipped** |

The reports are generated in `backend/target/surefire-reports/` and `backend/target/failsafe-reports/`. The build output isn't committed. `failsafe-summary.xml` recorded `completed=24, errors=0, failures=0, skipped=0, flakes=0`.

What the integration tests cover:

- **Fresh database:** a new empty container is migrated by `V1` and `V2` only (`flyway_schema_history` = `["1","2"]`). The context starts with `ddl-auto=validate`, so the entities match the schema.
- **Order API:**
  - `POST /api/orders` returns 201, a `Location` header and the snapshot.
  - `GET` returns an identical body (strict JSON comparison) read from PostgreSQL.
  - Each of these returns `application/problem+json` with the stated `code`:

    | Case | Status | `code` |
    |---|---|---|
    | Missing configuration id | 400 | `VALIDATION_FAILED` |
    | Missing quantity | 400 | `VALIDATION_FAILED` |
    | Quantity 2 (no order row added) | 400 | `VALIDATION_FAILED` |
    | Malformed JSON | 400 | `MALFORMED_REQUEST` |
    | Unknown configuration | 422 | `CONFIGURATION_NOT_ORDERABLE` |
    | Configuration without an approved recipe | 422 | `CONFIGURATION_NOT_ORDERABLE` |
    | Unknown order | 404 | `ORDER_NOT_FOUND` |
    | Order id that isn't a UUID | 400 | `INVALID_PARAMETER` |
- **Snapshot precision (`OrderSnapshotIT`):**
  1. Order A is placed for a test item (INR 1200.00, recipe revision 1).
  2. The product name, price and currency (to USD) change. Recipe revision 2 is inserted, and the configuration's `current_recipe_revision_id` is explicitly moved to it.
  3. Order A still returns its original name, `120000 INR`, the exact revision 1 id and number 1.
  4. A new order B returns the new name, `150000 USD`, and revision 2's id and number 2.
  5. A request that also sends `unitPriceMinor`, `currency`, `recipeRevisionId` and `total` still gets the catalog's price, currency and recipe.
- **Atomicity (`OrderAtomicityIT`):**
  - A temporary trigger makes the `order_line` INSERT fail for one test configuration. It only fires once the parent `customer_order` row is visible, which proves the order INSERT had already run in the same transaction.
  - `OrderService.placeOrder` fails with the injected root cause (`ERROR: injected order_line failure`, SQLSTATE P0001, wrapped as `JpaSystemException`). The `customer_order` count is unchanged, and no order exists without a line.
  - Over HTTP the same failure returns 500 `INTERNAL_ERROR` with a generic detail and no table names.
- **Database constraints (`DatabaseConstraintsIT`, plain SQL that bypasses Java):** PostgreSQL rejects each of these, and the test asserts the constraint name:
  - a second line for one order (`order_line_order_unique`);
  - quantity 2 (`order_line_quantity_is_one`);
  - NULL quantity (`not-null`);
  - a recipe revision from another configuration (`order_line_recipe_of_configuration_fk`, `product_configuration_current_recipe_fk`);
  - price 0 (`product_configuration_price_positive`);
  - an unknown order status (`customer_order_status_known`).

**Negative control:** I removed `@Max` from `PlaceOrderRequest`, on the working tree just before it was committed as `0fb442e`. `./mvnw verify -Dit.test=OrderApiIT` then failed with `quantityOtherThanOneIsRejectedAndNothingIsStored: Status expected:<400> but was:<500>`, `BUILD FAILURE`, exit code 1, and I restored the file.

- This shows that a failing integration test fails `verify`.
- It also shows the domain guard in `CustomerOrder.place` still refuses quantity 2. Without request validation, though, the client gets a 500 instead of a useful 400.

## 2. Manual demonstration: run, change catalog, restart, retrieve

The steps ran on a working tree with the same content as `0fb442e`; I committed straight afterwards with no edits in between. The commands were run from the repository root and from `backend/`.

1. `docker compose up -d db`: the container reported healthy. `docker compose ps` showed `127.0.0.1:5433->5432/tcp` and PostgreSQL 17.11.
2. `./mvnw spring-boot:run`: Flyway logged `Successfully applied 2 migrations to schema "public", now at version v2`. The app started in 2.396 s and `lsof` showed `java … TCP 127.0.0.1:8080 (LISTEN)`.
3. Created an order:

   ```text
   $ curl -s -i -X POST http://127.0.0.1:8080/api/orders -H "Content-Type: application/json" \
       -d '{"productConfigurationId":"0d3d0000-0000-4000-8000-000000000002","quantity":1}'
   HTTP/1.1 201
   Location: http://127.0.0.1:8080/api/orders/4af6c30c-5288-446a-89b5-2bbcf1bd8702
   {"id":"4af6c30c-5288-446a-89b5-2bbcf1bd8702","status":"AWAITING_PAYMENT","createdAt":"2026-10-07T08:06:54.575393Z",
    "line":{…,"productName":"Hex Desk Organizer (demo)","recipeRevisionId":"0d3d0000-0000-4000-8000-000000000003",
    "recipeRevisionNumber":1,"quantity":1,"unitPrice":{"amountMinor":49900,"currency":"INR"}},
    "total":{"amountMinor":49900,"currency":"INR"}}
   ```

4. Invalid input. Quantity 2 returned `HTTP/1.1 400`, `Content-Type: application/problem+json`, with this body:

   ```json
   {"code":"VALIDATION_FAILED","errors":[{"field":"quantity","message":"must be 1: a V1 order contains exactly one item"}]}
   ```

5. Catalog change. `UPDATE product_configuration SET price_minor = 59900 …` (`UPDATE 1`). The catalog endpoint then showed `59900`, while `GET /api/orders/4af6c30c-…` still returned `49900 INR`. I then restored the seed price to `49900`.
6. Restart. I stopped the application process and started `./mvnw spring-boot:run` again. Flyway logged `Schema "public" is up to date. No migration necessary.` `GET /api/orders/4af6c30c-5288-446a-89b5-2bbcf1bd8702` returned `HTTP/1.1 200` with the same body as step 3, `createdAt` included. In `psql`, one `customer_order` row joined to one `order_line` row with `49900 | INR | 1 | 1`.
7. Database container stop/start: after `docker compose stop` and `docker compose up -d db`, `select id, status from customer_order` returned `4af6c30c-5288-446a-89b5-2bbcf1bd8702|AWAITING_PAYMENT` from the named volume. The container was stopped, not removed or recreated.

## 3. Local-only exposure check

Checkpoint 1 has no authentication, so `server.address: 127.0.0.1` is in `application.yaml` and the Compose port is published as `127.0.0.1:5433:5432`.

From the same Mac, using its LAN address 192.168.0.140:

- `curl http://192.168.0.140:8080/api/catalog/configurations` failed to connect (curl exit 7, `http_code=000`).
- `nc -z 192.168.0.140 5433` failed (exit 1).
- The same requests to 127.0.0.1 succeeded.

I didn't test from a second machine, and there's no firewall assessment.

## 4. SQL illustration: why the transaction is required

The schema allows at most one line per order (`UNIQUE (order_id)`), but nothing makes a line exist. I ran this in `psql` against the local database:

- **(A) Two autocommit statements.** I inserted an order, then a line with quantity 2. The line failed on `order_line_quantity_is_one`, and the query found **1** order without a line. I then deleted that demo row.
- **(B) The same two statements between `BEGIN` and `COMMIT`.** The line failed the same way, PostgreSQL answered the `COMMIT` with `ROLLBACK`, and the query found **0**.

`OrderService.placeOrder` runs as in (B). The automated proof is `OrderAtomicityIT`.

## Known limitations

- No authentication or customer ownership; anyone who can reach 127.0.0.1:8080 can create and read orders. Checkpoint 4 adds identity, and nothing is deployed.
- Recipe revisions are append-only by convention. There is no update path in code, but no database trigger prevents an `UPDATE`.
- "Exactly one line per order" comes from `UNIQUE (order_id)` (at most one) plus the service transaction (at least one). A deferred constraint trigger could enforce existence in the database itself; that isn't implemented.
- There's no idempotency for a client that retries `POST /api/orders`, and two retries create two orders. Quote acceptance (Checkpoint 5) is where V1 prevents duplicate orders.
- Compose contains only PostgreSQL. I didn't test recreating the database container with its volume; that's part of Checkpoint 7.
- The catalog list isn't paginated, because V1 has a single seeded item.
- Mockito's agent prints a JDK dynamic-agent warning during tests; it doesn't affect results.

## Owner review (pending)

Harsha hasn't done the explanation/change/debug review yet. Running these checks doesn't establish personal understanding. To complete it:

1. **Explain:** trace `POST /api/orders` from HTTP → `OrderController` → `OrderService.placeOrder` → `CatalogService.findOrderable` → the SQL that Hibernate issues at commit. Say where the transaction starts and ends, and why the line copies values instead of joining to the catalog on read.
2. **Change an existing validation rule:** make `quantity` optional in `PlaceOrderRequest`, so that a missing quantity means 1 while 0 and 2 are still rejected with 400. Update `OrderApiIT.missingQuantityIsRejected` to the new expected behaviour, and run `./mvnw verify`.
3. **Explain the schema and snapshot:** using `V1__catalog_and_orders.sql`, explain:
   - which constraint stops a second line and which column property stops a NULL quantity;
   - why `order_line` has a composite foreign key to `recipe_revision`;
   - what would break if the order read its price from `product_configuration` instead.

Record the date and outcome here and in `IMPLEMENTED.md` only after Harsha has completed them himself.
