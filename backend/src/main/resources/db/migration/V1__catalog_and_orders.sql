-- Checkpoint 1 schema: a small catalog and one-line orders.
--
-- Constraints are named so that a violation message identifies the rule.
-- Money is stored in integer minor units (paise for INR) with its currency.

-- ---------------------------------------------------------------------------
-- Catalog
-- ---------------------------------------------------------------------------

CREATE TABLE product (
    id         uuid         NOT NULL,
    name       varchar(120) NOT NULL,
    created_at timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT product_pk PRIMARY KEY (id)
);

-- A sellable configuration of a product, with its current price.
-- current_recipe_revision_id is NULL until a recipe revision is approved;
-- a configuration without one cannot be ordered.
CREATE TABLE product_configuration (
    id                         uuid         NOT NULL,
    product_id                 uuid         NOT NULL,
    code                       varchar(40)  NOT NULL,
    description                varchar(200) NOT NULL,
    price_minor                bigint       NOT NULL,
    currency                   varchar(3)   NOT NULL,
    current_recipe_revision_id uuid,
    created_at                 timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT product_configuration_pk PRIMARY KEY (id),
    CONSTRAINT product_configuration_product_fk FOREIGN KEY (product_id) REFERENCES product (id),
    CONSTRAINT product_configuration_code_unique UNIQUE (code),
    CONSTRAINT product_configuration_price_positive CHECK (price_minor > 0),
    CONSTRAINT product_configuration_currency_format CHECK (currency ~ '^[A-Z]{3}$')
);

-- Approved manufacturing instructions for one configuration. Revisions are
-- append-only: a change is a new row with the next revision number, so an
-- order can keep pointing at the exact revision it was accepted with.
CREATE TABLE recipe_revision (
    id                       uuid         NOT NULL,
    product_configuration_id uuid         NOT NULL,
    revision_number          integer      NOT NULL,
    material                 varchar(80)  NOT NULL,
    layer_height_microns     integer      NOT NULL,
    notes                    varchar(500) NOT NULL,
    created_at               timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT recipe_revision_pk PRIMARY KEY (id),
    CONSTRAINT recipe_revision_configuration_fk
        FOREIGN KEY (product_configuration_id) REFERENCES product_configuration (id),
    CONSTRAINT recipe_revision_number_unique UNIQUE (product_configuration_id, revision_number),
    CONSTRAINT recipe_revision_number_positive CHECK (revision_number > 0),
    CONSTRAINT recipe_revision_layer_height_positive CHECK (layer_height_microns > 0),
    -- Allows the composite foreign keys below to require that a recipe
    -- revision belongs to a specific configuration.
    CONSTRAINT recipe_revision_id_configuration_unique UNIQUE (id, product_configuration_id)
);

-- The current recipe must be a revision of this same configuration.
ALTER TABLE product_configuration
    ADD CONSTRAINT product_configuration_current_recipe_fk
        FOREIGN KEY (current_recipe_revision_id, id)
        REFERENCES recipe_revision (id, product_configuration_id);

-- ---------------------------------------------------------------------------
-- Orders
-- ---------------------------------------------------------------------------

-- "order" is an SQL keyword, hence customer_order.
CREATE TABLE customer_order (
    id         uuid        NOT NULL,
    status     varchar(30) NOT NULL,
    created_at timestamptz NOT NULL,
    CONSTRAINT customer_order_pk PRIMARY KEY (id),
    -- Later checkpoints extend this list with a new migration.
    CONSTRAINT customer_order_status_known CHECK (status IN ('AWAITING_PAYMENT'))
);

-- The single line of an order, holding a snapshot of what was bought.
-- Name, description, price and currency are copied values, so later catalog
-- edits do not change an existing order. The recipe is referenced by the id
-- of the exact (append-only) revision that was current at order time.
--
-- order_line_order_unique allows at most one line per order. The database
-- does not require that a line exists: OrderService inserts the order and
-- its line in one transaction, so either both rows are committed or neither.
CREATE TABLE order_line (
    id                        uuid         NOT NULL,
    order_id                  uuid         NOT NULL,
    product_configuration_id  uuid         NOT NULL,
    recipe_revision_id        uuid         NOT NULL,
    product_name              varchar(120) NOT NULL,
    configuration_code        varchar(40)  NOT NULL,
    configuration_description varchar(200) NOT NULL,
    recipe_revision_number    integer      NOT NULL,
    quantity                  integer      NOT NULL,
    unit_price_minor          bigint       NOT NULL,
    currency                  varchar(3)   NOT NULL,
    CONSTRAINT order_line_pk PRIMARY KEY (id),
    CONSTRAINT order_line_order_fk FOREIGN KEY (order_id) REFERENCES customer_order (id),
    CONSTRAINT order_line_order_unique UNIQUE (order_id),
    CONSTRAINT order_line_configuration_fk
        FOREIGN KEY (product_configuration_id) REFERENCES product_configuration (id),
    CONSTRAINT order_line_recipe_of_configuration_fk
        FOREIGN KEY (recipe_revision_id, product_configuration_id)
        REFERENCES recipe_revision (id, product_configuration_id),
    CONSTRAINT order_line_quantity_is_one CHECK (quantity = 1),
    CONSTRAINT order_line_price_positive CHECK (unit_price_minor > 0),
    CONSTRAINT order_line_currency_format CHECK (currency ~ '^[A-Z]{3}$')
);
