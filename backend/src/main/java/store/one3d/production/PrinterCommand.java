package store.one3d.production;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

/**
 * The start command for one attempt, with a stable id. Sending the same id
 * again is safe: the simulator answers from its journal instead of starting a
 * second time. The payload hash lets the simulator refuse a reused id that
 * carries a different instruction.
 */
@Entity
@Table(name = "printer_command")
class PrinterCommand {

    @Id
    private UUID id;

    // Null marks a new row, so Spring Data persists it instead of merging (assigned id).
    @Version
    private Long version;

    @Column(name = "attempt_id", updatable = false)
    private UUID attemptId;

    @Column(name = "printer_id", updatable = false)
    private UUID printerId;

    @Column(name = "job_id", updatable = false)
    private UUID jobId;

    @Column(name = "payload_hash", updatable = false)
    private String payloadHash;

    @Column(name = "cleared_attempt_id", updatable = false)
    private UUID clearedAttemptId;

    @Enumerated(EnumType.STRING)
    private CommandStatus status;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected PrinterCommand() {
        // for JPA
    }

    static PrinterCommand start(PrintAttempt attempt, UUID recipeRevisionId, UUID clearedAttemptId, Instant now) {
        PrinterCommand command = new PrinterCommand();
        command.id = UUID.randomUUID();
        command.attemptId = attempt.getId();
        command.printerId = attempt.getPrinterId();
        command.jobId = attempt.getJob().getId();
        command.clearedAttemptId = clearedAttemptId;
        command.payloadHash = sha256(String.join("|",
                command.id.toString(), command.printerId.toString(), command.attemptId.toString(),
                command.jobId.toString(), String.valueOf(recipeRevisionId), String.valueOf(clearedAttemptId)));
        command.status = CommandStatus.PENDING;
        command.createdAt = now;
        command.updatedAt = now;
        return command;
    }

    void record(CommandStatus newStatus, Instant now) {
        status = newStatus;
        updatedAt = now;
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }

    UUID getId() {
        return id;
    }

    UUID getAttemptId() {
        return attemptId;
    }

    UUID getPrinterId() {
        return printerId;
    }

    UUID getJobId() {
        return jobId;
    }

    String getPayloadHash() {
        return payloadHash;
    }

    UUID getClearedAttemptId() {
        return clearedAttemptId;
    }

    CommandStatus getStatus() {
        return status;
    }
}
