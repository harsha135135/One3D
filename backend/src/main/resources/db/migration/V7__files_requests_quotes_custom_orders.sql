-- Checkpoint 5: private STL files, custom requests with immutable submission
-- revisions, manual quotes, and orders created from accepted quotes.

-- ---------------------------------------------------------------------------
-- Files
-- ---------------------------------------------------------------------------

-- An uploaded model. The browser uploads straight to private storage; this row
-- records who owns it and, once finalized, the exact stored version that was
-- checked. Approval and orders always refer to that version and digest, never
-- to "whatever is at this key now".
CREATE TABLE artifact (
    id                uuid         NOT NULL,
    owner_id          uuid         NOT NULL,
    storage_key       varchar(200) NOT NULL,
    original_filename varchar(200) NOT NULL,
    declared_size     bigint       NOT NULL,
    status            varchar(20)  NOT NULL,
    version_id        varchar(200),
    sha256            varchar(64),
    verified_size     bigint,
    rejection_reason  varchar(500),
    created_at        timestamptz  NOT NULL,
    upload_expires_at timestamptz  NOT NULL,
    finalized_at      timestamptz,
    verified_at       timestamptz,
    CONSTRAINT artifact_pk PRIMARY KEY (id),
    CONSTRAINT artifact_owner_fk FOREIGN KEY (owner_id) REFERENCES app_user (id),
    CONSTRAINT artifact_key_unique UNIQUE (storage_key),
    CONSTRAINT artifact_status_known CHECK (status IN ('UPLOAD_PENDING', 'VERIFYING', 'VERIFIED', 'REJECTED')),
    -- V1 accepts one STL of at most 20 MB.
    CONSTRAINT artifact_declared_size_bounded CHECK (declared_size BETWEEN 1 AND 20971520),
    -- After finalization the exact stored version is pinned.
    CONSTRAINT artifact_version_pinned_after_upload CHECK ((status = 'UPLOAD_PENDING') = (version_id IS NULL)),
    -- A verified file always has its digest and real size.
    CONSTRAINT artifact_verified_has_digest
        CHECK (status <> 'VERIFIED' OR (sha256 IS NOT NULL AND verified_size IS NOT NULL)),
    CONSTRAINT artifact_rejected_has_reason CHECK ((status = 'REJECTED') = (rejection_reason IS NOT NULL))
);

-- ---------------------------------------------------------------------------
-- Custom requests and their immutable revisions
-- ---------------------------------------------------------------------------

CREATE TABLE custom_request (
    id               uuid          NOT NULL,
    customer_id      uuid          NOT NULL,
    status           varchar(20)   NOT NULL,
    operator_message varchar(1000),
    created_at       timestamptz   NOT NULL,
    updated_at       timestamptz   NOT NULL,
    version          bigint        NOT NULL DEFAULT 0,
    CONSTRAINT custom_request_pk PRIMARY KEY (id),
    CONSTRAINT custom_request_customer_fk FOREIGN KEY (customer_id) REFERENCES app_user (id),
    CONSTRAINT custom_request_status_known
        CHECK (status IN ('SUBMITTED', 'CHANGES_REQUIRED', 'REJECTED', 'QUOTED', 'ORDERED'))
);

CREATE INDEX custom_request_by_customer ON custom_request (customer_id, created_at DESC);
CREATE INDEX custom_request_by_status ON custom_request (status, created_at);

-- One model file plus the customer's requirements. Never edited: a changed
-- file or changed requirements are a new revision that must be reviewed again.
CREATE TABLE submission_revision (
    id              uuid          NOT NULL,
    request_id      uuid          NOT NULL,
    revision_number integer       NOT NULL,
    artifact_id     uuid          NOT NULL,
    material        varchar(80)   NOT NULL,
    colour          varchar(80)   NOT NULL,
    notes           varchar(1000) NOT NULL,
    created_at      timestamptz   NOT NULL,
    CONSTRAINT submission_revision_pk PRIMARY KEY (id),
    CONSTRAINT submission_revision_request_fk FOREIGN KEY (request_id) REFERENCES custom_request (id),
    CONSTRAINT submission_revision_artifact_fk FOREIGN KEY (artifact_id) REFERENCES artifact (id),
    CONSTRAINT submission_revision_number_unique UNIQUE (request_id, revision_number),
    CONSTRAINT submission_revision_number_positive CHECK (revision_number > 0),
    -- A file belongs to exactly one revision.
    CONSTRAINT submission_revision_artifact_unique UNIQUE (artifact_id),
    -- Lets a quote require that its revision belongs to its request.
    CONSTRAINT submission_revision_id_request_unique UNIQUE (id, request_id)
);

-- ---------------------------------------------------------------------------
-- Quotes
-- ---------------------------------------------------------------------------

-- A price for one exact revision, valid until expires_at. Accepting it creates
-- at most one order (quote.order_id and order_line.quote_id are unique).
CREATE TABLE quote (
    id           uuid        NOT NULL,
    request_id   uuid        NOT NULL,
    revision_id  uuid        NOT NULL,
    price_minor  bigint      NOT NULL,
    currency     varchar(3)  NOT NULL,
    status       varchar(20) NOT NULL,
    issued_at    timestamptz NOT NULL,
    expires_at   timestamptz NOT NULL,
    accepted_at  timestamptz,
    withdrawn_at timestamptz,
    order_id     uuid,
    version      bigint      NOT NULL DEFAULT 0,
    CONSTRAINT quote_pk PRIMARY KEY (id),
    CONSTRAINT quote_request_fk FOREIGN KEY (request_id) REFERENCES custom_request (id),
    CONSTRAINT quote_revision_of_request_fk
        FOREIGN KEY (revision_id, request_id) REFERENCES submission_revision (id, request_id),
    CONSTRAINT quote_order_fk FOREIGN KEY (order_id) REFERENCES customer_order (id),
    CONSTRAINT quote_order_unique UNIQUE (order_id),
    CONSTRAINT quote_price_positive CHECK (price_minor > 0),
    CONSTRAINT quote_currency_format CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT quote_status_known CHECK (status IN ('ISSUED', 'ACCEPTED', 'WITHDRAWN')),
    CONSTRAINT quote_expiry_after_issue CHECK (expires_at > issued_at),
    CONSTRAINT quote_accepted_has_order
        CHECK ((status = 'ACCEPTED') = (order_id IS NOT NULL AND accepted_at IS NOT NULL)),
    CONSTRAINT quote_withdrawn_has_time CHECK ((status = 'WITHDRAWN') = (withdrawn_at IS NOT NULL))
);

-- A request has at most one open quote at a time.
CREATE UNIQUE INDEX quote_one_issued_per_request ON quote (request_id) WHERE status = 'ISSUED';

-- ---------------------------------------------------------------------------
-- Recipe revisions owned by a catalog configuration or by a quote
-- ---------------------------------------------------------------------------

-- Catalog recipes belong to a configuration; a custom part's recipe belongs to
-- the quote that priced it. Exactly one owner, enforced here.
ALTER TABLE recipe_revision
    ALTER COLUMN product_configuration_id DROP NOT NULL,
    ADD COLUMN quote_id uuid,
    ADD CONSTRAINT recipe_revision_quote_fk FOREIGN KEY (quote_id) REFERENCES quote (id),
    ADD CONSTRAINT recipe_revision_one_owner CHECK (num_nonnulls(product_configuration_id, quote_id) = 1),
    ADD CONSTRAINT recipe_revision_quote_unique UNIQUE (quote_id),
    ADD CONSTRAINT recipe_revision_id_quote_unique UNIQUE (id, quote_id);

-- ---------------------------------------------------------------------------
-- Order lines from the catalog or from an accepted quote
-- ---------------------------------------------------------------------------

-- CATALOG lines name a configuration; CUSTOM_QUOTE lines name a quote. Each
-- composite foreign key checks that the recipe revision belongs to that owner
-- (MATCH SIMPLE: it applies only when its owner column is set). For custom
-- lines the snapshot columns hold the quote's item: product_name is
-- "Custom part: <file>", configuration_code "CUSTOM-<quote>", and
-- configuration_description the material and colour.
ALTER TABLE order_line
    ADD COLUMN source varchar(20) NOT NULL DEFAULT 'CATALOG',
    ADD COLUMN quote_id uuid,
    ALTER COLUMN product_configuration_id DROP NOT NULL,
    ADD CONSTRAINT order_line_quote_fk FOREIGN KEY (quote_id) REFERENCES quote (id),
    ADD CONSTRAINT order_line_quote_unique UNIQUE (quote_id),
    ADD CONSTRAINT order_line_source_known CHECK (source IN ('CATALOG', 'CUSTOM_QUOTE')),
    ADD CONSTRAINT order_line_source_matches_owner CHECK (
        (source = 'CATALOG' AND product_configuration_id IS NOT NULL AND quote_id IS NULL)
        OR (source = 'CUSTOM_QUOTE' AND quote_id IS NOT NULL AND product_configuration_id IS NULL)),
    ADD CONSTRAINT order_line_recipe_of_quote_fk
        FOREIGN KEY (recipe_revision_id, quote_id) REFERENCES recipe_revision (id, quote_id);

ALTER TABLE order_line ALTER COLUMN source DROP DEFAULT;

-- ---------------------------------------------------------------------------
-- Background work: artifact verification joins start-command delivery
-- ---------------------------------------------------------------------------

ALTER TABLE pending_work
    ALTER COLUMN command_id DROP NOT NULL,
    ADD COLUMN artifact_id uuid,
    ADD CONSTRAINT pending_work_artifact_fk FOREIGN KEY (artifact_id) REFERENCES artifact (id),
    DROP CONSTRAINT pending_work_kind_known,
    ADD CONSTRAINT pending_work_kind_known CHECK (kind IN ('DISPATCH_START', 'VERIFY_ARTIFACT')),
    -- Each kind of work refers to exactly its own kind of record.
    ADD CONSTRAINT pending_work_subject_matches_kind CHECK (
        (kind = 'DISPATCH_START') = (command_id IS NOT NULL)
        AND (kind = 'VERIFY_ARTIFACT') = (artifact_id IS NOT NULL)),
    ADD CONSTRAINT pending_work_one_per_artifact UNIQUE (kind, artifact_id);
