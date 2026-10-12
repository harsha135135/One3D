-- Checkpoint 6: test-mode payments, handoff and the customer timeline.

-- ---------------------------------------------------------------------------
-- Orders: payment window, handoff and an optimistic version
-- ---------------------------------------------------------------------------

-- payment_window_ends_at: the last moment a checkout may be open for the order.
-- A custom order inherits its accepted quote's validity; a catalog order gets
-- a fixed window from when it was placed (application setting; 72 hours here).
ALTER TABLE customer_order
    ADD COLUMN handed_over_at timestamptz,
    ADD COLUMN payment_window_ends_at timestamptz,
    ADD COLUMN version bigint NOT NULL DEFAULT 0;

UPDATE customer_order o
SET payment_window_ends_at = coalesce(
        (SELECT q.expires_at FROM order_line l JOIN quote q ON q.id = l.quote_id WHERE l.order_id = o.id),
        o.created_at + interval '72 hours');

ALTER TABLE customer_order
    ALTER COLUMN payment_window_ends_at SET NOT NULL,
    DROP CONSTRAINT customer_order_status_known,
    ADD CONSTRAINT customer_order_status_known
        CHECK (status IN ('AWAITING_PAYMENT', 'CONFIRMED', 'READY_FOR_HANDOFF', 'HANDED_OVER')),
    DROP CONSTRAINT customer_order_confirmation_source_known,
    ADD CONSTRAINT customer_order_confirmation_source_known
        CHECK (confirmation_source IN ('DEMO_SIMULATED', 'RAZORPAY_TEST')),
    ADD CONSTRAINT customer_order_ready_matches_status
        CHECK ((ready_for_handoff_at IS NOT NULL) = (status IN ('READY_FOR_HANDOFF', 'HANDED_OVER'))),
    ADD CONSTRAINT customer_order_handed_over_matches_status
        CHECK ((handed_over_at IS NOT NULL) = (status = 'HANDED_OVER'));

-- ---------------------------------------------------------------------------
-- Checkouts: one provider order per payment attempt
-- ---------------------------------------------------------------------------

-- A checkout is created before the provider is called; provider_order_id is
-- filled in once the provider answers. A customer may open a new checkout
-- (the old one is SUPERSEDED), but only one is OPEN at a time.
CREATE TABLE checkout (
    id                uuid        NOT NULL,
    order_id          uuid        NOT NULL,
    provider_order_id varchar(64),
    amount_minor      bigint      NOT NULL,
    currency          varchar(3)  NOT NULL,
    status            varchar(20) NOT NULL,
    created_at        timestamptz NOT NULL,
    expires_at        timestamptz NOT NULL,
    CONSTRAINT checkout_pk PRIMARY KEY (id),
    CONSTRAINT checkout_order_fk FOREIGN KEY (order_id) REFERENCES customer_order (id),
    CONSTRAINT checkout_provider_order_unique UNIQUE (provider_order_id),
    CONSTRAINT checkout_status_known CHECK (status IN ('OPEN', 'PAID', 'SUPERSEDED')),
    CONSTRAINT checkout_amount_positive CHECK (amount_minor > 0),
    CONSTRAINT checkout_currency_format CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT checkout_expiry_after_creation CHECK (expires_at > created_at),
    CONSTRAINT checkout_paid_has_provider_order CHECK (status <> 'PAID' OR provider_order_id IS NOT NULL)
);

CREATE UNIQUE INDEX checkout_one_open_per_order ON checkout (order_id) WHERE status = 'OPEN';
CREATE INDEX checkout_by_order ON checkout (order_id, created_at);

-- ---------------------------------------------------------------------------
-- Payments: every distinct captured payment the provider reported
-- ---------------------------------------------------------------------------

-- provider_payment_id is unique: the same payment reported twice (by two
-- events, or by a webhook and a reconciliation) is recorded once. At most one
-- payment per order is APPLIED (confirms it); anything inconsistent is kept as
-- NEEDS_REVIEW for an operator and never changes the order.
CREATE TABLE payment (
    id                  uuid         NOT NULL,
    provider_payment_id varchar(64)  NOT NULL,
    provider_order_id   varchar(64)  NOT NULL,
    checkout_id         uuid,
    order_id            uuid,
    amount_minor        bigint       NOT NULL,
    currency            varchar(3)   NOT NULL,
    provider_created_at timestamptz  NOT NULL,
    recorded_at         timestamptz  NOT NULL,
    source              varchar(20)  NOT NULL,
    outcome             varchar(20)  NOT NULL,
    review_reason       varchar(30),
    resolved_at         timestamptz,
    resolution_note     varchar(1000),
    CONSTRAINT payment_pk PRIMARY KEY (id),
    CONSTRAINT payment_provider_payment_unique UNIQUE (provider_payment_id),
    CONSTRAINT payment_checkout_fk FOREIGN KEY (checkout_id) REFERENCES checkout (id),
    CONSTRAINT payment_order_fk FOREIGN KEY (order_id) REFERENCES customer_order (id),
    CONSTRAINT payment_source_known CHECK (source IN ('WEBHOOK', 'RECONCILIATION')),
    CONSTRAINT payment_outcome_known CHECK (outcome IN ('APPLIED', 'NEEDS_REVIEW')),
    CONSTRAINT payment_review_reason_known CHECK (review_reason IN
        ('UNKNOWN_PROVIDER_ORDER', 'AMOUNT_MISMATCH', 'LATE_PAYMENT', 'SECOND_PAYMENT')),
    CONSTRAINT payment_review_has_reason CHECK ((outcome = 'NEEDS_REVIEW') = (review_reason IS NOT NULL)),
    CONSTRAINT payment_applied_has_order CHECK (outcome <> 'APPLIED' OR (order_id IS NOT NULL AND checkout_id IS NOT NULL)),
    CONSTRAINT payment_resolution_only_for_review CHECK (resolved_at IS NULL OR outcome = 'NEEDS_REVIEW'),
    CONSTRAINT payment_resolution_has_note CHECK ((resolved_at IS NULL) = (resolution_note IS NULL))
);

CREATE UNIQUE INDEX payment_one_applied_per_order ON payment (order_id) WHERE outcome = 'APPLIED';
CREATE INDEX payment_needing_review ON payment (recorded_at) WHERE outcome = 'NEEDS_REVIEW' AND resolved_at IS NULL;

-- ---------------------------------------------------------------------------
-- Webhook receipts: one row per provider event id
-- ---------------------------------------------------------------------------

-- Written in the same transaction as the event's effect, before the webhook
-- is acknowledged. Only identifiers and the outcome are kept: provider
-- payloads contain customer contact details.
CREATE TABLE payment_event (
    id                  uuid        NOT NULL,
    provider_event_id   varchar(64) NOT NULL,
    event_type          varchar(60) NOT NULL,
    provider_payment_id varchar(64),
    provider_order_id   varchar(64),
    received_at         timestamptz NOT NULL,
    outcome             varchar(20) NOT NULL,
    CONSTRAINT payment_event_pk PRIMARY KEY (id),
    CONSTRAINT payment_event_provider_event_unique UNIQUE (provider_event_id),
    CONSTRAINT payment_event_outcome_known
        CHECK (outcome IN ('RECEIVED', 'APPLIED', 'ALREADY_RECORDED', 'NEEDS_REVIEW', 'IGNORED'))
);

-- ---------------------------------------------------------------------------
-- Customer timeline: facts recorded by the module that caused them
-- ---------------------------------------------------------------------------

CREATE TABLE order_fact (
    id          uuid         NOT NULL,
    order_id    uuid         NOT NULL,
    kind        varchar(30)  NOT NULL,
    occurred_at timestamptz  NOT NULL,
    detail      varchar(200),
    CONSTRAINT order_fact_pk PRIMARY KEY (id),
    CONSTRAINT order_fact_order_fk FOREIGN KEY (order_id) REFERENCES customer_order (id),
    CONSTRAINT order_fact_kind_known CHECK (kind IN ('PLACED', 'CONFIRMED', 'PAYMENT_UNDER_REVIEW',
        'RELEASED_TO_PRODUCTION', 'QC_PASSED', 'READY_FOR_HANDOFF', 'HANDED_OVER'))
);

-- Milestones happen once per order; a payment review can happen more than once.
CREATE UNIQUE INDEX order_fact_once_per_order ON order_fact (order_id, kind) WHERE kind <> 'PAYMENT_UNDER_REVIEW';
CREATE INDEX order_fact_by_order ON order_fact (order_id, occurred_at);

-- Facts for orders that existed before this migration, from their timestamps.
-- Demo confirmations say plainly that no payment was taken.
INSERT INTO order_fact (id, order_id, kind, occurred_at, detail)
SELECT gen_random_uuid(), id, 'PLACED', created_at, NULL FROM customer_order;

INSERT INTO order_fact (id, order_id, kind, occurred_at, detail)
SELECT gen_random_uuid(), id, 'CONFIRMED', confirmed_at,
       CASE confirmation_source WHEN 'DEMO_SIMULATED' THEN 'Confirmed by the demo shortcut; no payment was taken' END
FROM customer_order WHERE confirmed_at IS NOT NULL;

INSERT INTO order_fact (id, order_id, kind, occurred_at, detail)
SELECT gen_random_uuid(), order_id, 'RELEASED_TO_PRODUCTION', released_at, NULL FROM production_job;

INSERT INTO order_fact (id, order_id, kind, occurred_at, detail)
SELECT gen_random_uuid(), order_id, 'QC_PASSED', accepted_at, NULL FROM production_job WHERE accepted_at IS NOT NULL;

INSERT INTO order_fact (id, order_id, kind, occurred_at, detail)
SELECT gen_random_uuid(), id, 'READY_FOR_HANDOFF', ready_for_handoff_at, NULL
FROM customer_order WHERE ready_for_handoff_at IS NOT NULL;
