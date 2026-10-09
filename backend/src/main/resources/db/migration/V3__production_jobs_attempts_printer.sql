-- Checkpoint 2: order confirmation and handoff readiness, production jobs,
-- print attempts, one simulated printer and whole-part QC.
--
-- Order, job and attempt have separate states on purpose: the order tracks
-- the commercial agreement, the job tracks the manufacturing work for that
-- order, and each attempt records one execution on a printer.

-- ---------------------------------------------------------------------------
-- Orders: confirmation and readiness for handoff
-- ---------------------------------------------------------------------------

ALTER TABLE customer_order
    ADD COLUMN confirmed_at         timestamptz,
    -- How the order became confirmed. Until real payments exist (Checkpoint 6)
    -- the only source is the demo-only simulated payment, and the data says so.
    ADD COLUMN confirmation_source  varchar(30),
    ADD COLUMN ready_for_handoff_at timestamptz;

ALTER TABLE customer_order
    DROP CONSTRAINT customer_order_status_known,
    ADD CONSTRAINT customer_order_status_known
        CHECK (status IN ('AWAITING_PAYMENT', 'CONFIRMED', 'READY_FOR_HANDOFF')),
    ADD CONSTRAINT customer_order_confirmation_source_known
        CHECK (confirmation_source IN ('DEMO_SIMULATED')),
    -- An order is confirmed exactly when it has left AWAITING_PAYMENT, and a
    -- confirmation always records when and how it happened.
    ADD CONSTRAINT customer_order_confirmation_matches_status
        CHECK ((confirmed_at IS NULL) = (status = 'AWAITING_PAYMENT')
           AND (confirmation_source IS NULL) = (confirmed_at IS NULL));

-- ---------------------------------------------------------------------------
-- Printer (simulated only in V1)
-- ---------------------------------------------------------------------------

-- current_attempt_id is the attempt that currently owns the printer (null when
-- idle). bed_state is tracked separately: a finished or failed print leaves the
-- bed OCCUPIED until an operator clears it, even though the printer is idle.
CREATE TABLE printer (
    id                   uuid        NOT NULL,
    name                 varchar(80) NOT NULL,
    simulated            boolean     NOT NULL,
    bed_state            varchar(20) NOT NULL,
    current_attempt_id   uuid,
    bed_state_changed_at timestamptz NOT NULL,
    CONSTRAINT printer_pk PRIMARY KEY (id),
    CONSTRAINT printer_name_unique UNIQUE (name),
    -- V1 never controls a real printer.
    CONSTRAINT printer_simulated_only CHECK (simulated),
    CONSTRAINT printer_bed_state_known CHECK (bed_state IN ('CLEAR', 'OCCUPIED')),
    -- A running attempt always occupies the bed.
    CONSTRAINT printer_running_attempt_occupies_bed
        CHECK (current_attempt_id IS NULL OR bed_state = 'OCCUPIED')
);

-- ---------------------------------------------------------------------------
-- Production job: the manufacturing work for one order
-- ---------------------------------------------------------------------------

-- Created only by an explicit operator release, never by placing or paying
-- for an order. V1 has one job per order.
CREATE TABLE production_job (
    id                 uuid        NOT NULL,
    order_id           uuid        NOT NULL,
    recipe_revision_id uuid        NOT NULL,
    status             varchar(30) NOT NULL,
    released_at        timestamptz NOT NULL,
    accepted_at        timestamptz,
    CONSTRAINT production_job_pk PRIMARY KEY (id),
    CONSTRAINT production_job_order_fk FOREIGN KEY (order_id) REFERENCES customer_order (id),
    CONSTRAINT production_job_order_unique UNIQUE (order_id),
    CONSTRAINT production_job_recipe_fk FOREIGN KEY (recipe_revision_id) REFERENCES recipe_revision (id),
    CONSTRAINT production_job_status_known
        CHECK (status IN ('RELEASED', 'PRINTING', 'AWAITING_QC', 'ACCEPTED'))
);

-- ---------------------------------------------------------------------------
-- Print attempt: one execution of a job on a printer
-- ---------------------------------------------------------------------------

-- Attempts are never deleted or reused: a retry is a new row with the next
-- attempt number, so earlier failures stay on record.
CREATE TABLE print_attempt (
    id             uuid         NOT NULL,
    job_id         uuid         NOT NULL,
    attempt_number integer      NOT NULL,
    printer_id     uuid         NOT NULL,
    status         varchar(20)  NOT NULL,
    started_at     timestamptz  NOT NULL,
    ended_at       timestamptz,
    failure_reason varchar(500),
    qc_result      varchar(10),
    qc_notes       varchar(500),
    qc_recorded_at timestamptz,
    CONSTRAINT print_attempt_pk PRIMARY KEY (id),
    CONSTRAINT print_attempt_job_fk FOREIGN KEY (job_id) REFERENCES production_job (id),
    CONSTRAINT print_attempt_printer_fk FOREIGN KEY (printer_id) REFERENCES printer (id),
    CONSTRAINT print_attempt_number_unique UNIQUE (job_id, attempt_number),
    CONSTRAINT print_attempt_number_positive CHECK (attempt_number > 0),
    CONSTRAINT print_attempt_status_known CHECK (status IN ('RUNNING', 'COMPLETED', 'FAILED')),
    -- A failed attempt keeps the reason it failed.
    CONSTRAINT print_attempt_failure_has_reason
        CHECK ((status = 'FAILED') = (failure_reason IS NOT NULL)),
    CONSTRAINT print_attempt_qc_result_known CHECK (qc_result IN ('PASSED', 'FAILED')),
    -- Only a completed print can be inspected; failed output never passes QC.
    CONSTRAINT print_attempt_qc_only_when_completed
        CHECK (qc_result IS NULL OR status = 'COMPLETED')
);

ALTER TABLE printer
    ADD CONSTRAINT printer_current_attempt_fk
        FOREIGN KEY (current_attempt_id) REFERENCES print_attempt (id);
