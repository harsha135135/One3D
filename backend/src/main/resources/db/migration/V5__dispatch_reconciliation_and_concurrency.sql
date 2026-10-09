-- Checkpoint 3: dispatch to the separate simulator, reconciliation of
-- uncertain starts, bed cycles and database-level protection against
-- competing ownership.

-- ---------------------------------------------------------------------------
-- Attempt states
-- ---------------------------------------------------------------------------

-- START_INTENT:            reserved and recorded; the command is not yet confirmed.
-- RECONCILIATION_REQUIRED: the start may or may not have happened (for example
--                          the reply was lost). The attempt keeps the printer and
--                          the job until the simulator's journal answers.
-- REJECTED_BEFORE_START:   the simulator refused the command; nothing printed.
ALTER TABLE print_attempt
    ALTER COLUMN status TYPE varchar(30),
    DROP CONSTRAINT print_attempt_status_known,
    ADD CONSTRAINT print_attempt_status_known CHECK (status IN (
        'START_INTENT', 'RUNNING', 'COMPLETED', 'FAILED',
        'REJECTED_BEFORE_START', 'RECONCILIATION_REQUIRED')),
    DROP CONSTRAINT print_attempt_failure_has_reason,
    ADD CONSTRAINT print_attempt_failure_has_reason
        CHECK ((status IN ('FAILED', 'REJECTED_BEFORE_START')) = (failure_reason IS NOT NULL));

-- At most one active attempt per printer and per job, whatever the code does.
CREATE UNIQUE INDEX print_attempt_one_active_per_printer ON print_attempt (printer_id)
    WHERE status IN ('START_INTENT', 'RUNNING', 'RECONCILIATION_REQUIRED');
CREATE UNIQUE INDEX print_attempt_one_active_per_job ON print_attempt (job_id)
    WHERE status IN ('START_INTENT', 'RUNNING', 'RECONCILIATION_REQUIRED');

-- Optimistic locking for ordinary edits (JPA @Version).
ALTER TABLE print_attempt ADD COLUMN version bigint NOT NULL DEFAULT 0;
ALTER TABLE production_job ADD COLUMN version bigint NOT NULL DEFAULT 0;
ALTER TABLE printer ADD COLUMN version bigint NOT NULL DEFAULT 0;

-- ---------------------------------------------------------------------------
-- Bed cycles and clearances
-- ---------------------------------------------------------------------------

-- The attempt whose print occupies the bed. A clearance must name it, so a
-- delayed clearance for an earlier print can never clear a later one.
ALTER TABLE printer ADD COLUMN bed_cycle_attempt_id uuid;

-- Existing occupied beds belong to the printer's most recent attempt.
UPDATE printer p
SET bed_cycle_attempt_id = (
    SELECT a.id FROM print_attempt a
    WHERE a.printer_id = p.id
    ORDER BY a.started_at DESC, a.attempt_number DESC
    LIMIT 1)
WHERE p.bed_state = 'OCCUPIED';

ALTER TABLE printer
    ADD CONSTRAINT printer_bed_cycle_attempt_fk
        FOREIGN KEY (bed_cycle_attempt_id) REFERENCES print_attempt (id),
    ADD CONSTRAINT printer_bed_cycle_matches_state
        CHECK ((bed_state = 'OCCUPIED') = (bed_cycle_attempt_id IS NOT NULL));

-- An operator's confirmation that one print was removed from the bed. The next
-- start consumes it and passes it to the simulator with the start command.
CREATE TABLE bed_clearance (
    id                     uuid        NOT NULL,
    printer_id             uuid        NOT NULL,
    cleared_attempt_id     uuid        NOT NULL,
    consumed_by_attempt_id uuid,
    cleared_at             timestamptz NOT NULL,
    version                bigint      NOT NULL DEFAULT 0,
    CONSTRAINT bed_clearance_pk PRIMARY KEY (id),
    CONSTRAINT bed_clearance_printer_fk FOREIGN KEY (printer_id) REFERENCES printer (id),
    CONSTRAINT bed_clearance_cleared_attempt_fk FOREIGN KEY (cleared_attempt_id) REFERENCES print_attempt (id),
    CONSTRAINT bed_clearance_consumed_by_fk FOREIGN KEY (consumed_by_attempt_id) REFERENCES print_attempt (id),
    -- A print is cleared once, and a clearance is used by at most one start.
    CONSTRAINT bed_clearance_cleared_attempt_unique UNIQUE (cleared_attempt_id),
    CONSTRAINT bed_clearance_consumed_by_unique UNIQUE (consumed_by_attempt_id)
);

-- A bed can only be waiting for one start at a time.
CREATE UNIQUE INDEX bed_clearance_one_open_per_printer ON bed_clearance (printer_id)
    WHERE consumed_by_attempt_id IS NULL;

-- ---------------------------------------------------------------------------
-- Commands to the simulator and the durable work queue
-- ---------------------------------------------------------------------------

-- One start command per attempt, with a stable id. Resending the same id is
-- safe: the simulator returns its journaled decision instead of starting again.
CREATE TABLE printer_command (
    id                 uuid        NOT NULL,
    attempt_id         uuid        NOT NULL,
    printer_id         uuid        NOT NULL,
    job_id             uuid        NOT NULL,
    payload_hash       varchar(64) NOT NULL,
    cleared_attempt_id uuid,
    status             varchar(20) NOT NULL,
    created_at         timestamptz NOT NULL,
    updated_at         timestamptz NOT NULL,
    version            bigint      NOT NULL DEFAULT 0,
    CONSTRAINT printer_command_pk PRIMARY KEY (id),
    CONSTRAINT printer_command_attempt_fk FOREIGN KEY (attempt_id) REFERENCES print_attempt (id),
    CONSTRAINT printer_command_attempt_unique UNIQUE (attempt_id),
    CONSTRAINT printer_command_printer_fk FOREIGN KEY (printer_id) REFERENCES printer (id),
    CONSTRAINT printer_command_job_fk FOREIGN KEY (job_id) REFERENCES production_job (id),
    -- PENDING: not yet confirmed; UNCERTAIN: delivery outcome unknown.
    CONSTRAINT printer_command_status_known
        CHECK (status IN ('PENDING', 'ACKNOWLEDGED', 'REJECTED', 'UNCERTAIN'))
);

-- Follow-up work created in the same transaction as the business change, so
-- it cannot be lost. Workers claim rows with FOR UPDATE SKIP LOCKED and a
-- lease; every later update must present the current claim_token, so a worker
-- whose lease expired cannot overwrite the result of the worker that replaced it.
CREATE TABLE pending_work (
    id            uuid         NOT NULL,
    kind          varchar(30)  NOT NULL,
    command_id    uuid         NOT NULL,
    status        varchar(20)  NOT NULL,
    attempts      integer      NOT NULL DEFAULT 0,
    max_attempts  integer      NOT NULL,
    available_at  timestamptz  NOT NULL,
    claim_token   uuid,
    claimed_until timestamptz,
    last_error    varchar(500),
    created_at    timestamptz  NOT NULL,
    updated_at    timestamptz  NOT NULL,
    CONSTRAINT pending_work_pk PRIMARY KEY (id),
    CONSTRAINT pending_work_command_fk FOREIGN KEY (command_id) REFERENCES printer_command (id),
    CONSTRAINT pending_work_kind_known CHECK (kind IN ('DISPATCH_START')),
    CONSTRAINT pending_work_status_known CHECK (status IN ('PENDING', 'CLAIMED', 'DONE', 'EXHAUSTED')),
    CONSTRAINT pending_work_one_per_command UNIQUE (kind, command_id),
    CONSTRAINT pending_work_attempts_bounded CHECK (attempts >= 0 AND attempts <= max_attempts),
    CONSTRAINT pending_work_claim_matches_status
        CHECK ((status = 'CLAIMED') = (claim_token IS NOT NULL AND claimed_until IS NOT NULL))
);

CREATE INDEX pending_work_ready ON pending_work (available_at) WHERE status IN ('PENDING', 'CLAIMED');
