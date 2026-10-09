-- Checkpoint 4: people and who owns which order.
--
-- A user is identified by the identity provider that vouches for them
-- (issuer) and the id it gives them (subject). The subject alone is only
-- unique within one issuer. Roles are not stored here: they come from the
-- provider's groups claim at every login.

CREATE TABLE app_user (
    id         uuid         NOT NULL,
    issuer     varchar(200) NOT NULL,
    subject    varchar(200) NOT NULL,
    email      varchar(320),
    created_at timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT app_user_pk PRIMARY KEY (id),
    CONSTRAINT app_user_identity_unique UNIQUE (issuer, subject)
);

-- Orders placed before identity existed (local demo data) belong to this
-- synthetic user, so every order has an owner.
INSERT INTO app_user (id, issuer, subject, email)
VALUES ('0d3d0000-0000-4000-8000-000000000201', 'urn:one3d:legacy', 'legacy-demo-customer', NULL);

ALTER TABLE customer_order ADD COLUMN customer_id uuid;

UPDATE customer_order SET customer_id = '0d3d0000-0000-4000-8000-000000000201' WHERE customer_id IS NULL;

ALTER TABLE customer_order
    ALTER COLUMN customer_id SET NOT NULL,
    ADD CONSTRAINT customer_order_customer_fk FOREIGN KEY (customer_id) REFERENCES app_user (id);

-- "My orders", newest first.
CREATE INDEX customer_order_by_customer ON customer_order (customer_id, created_at DESC);
