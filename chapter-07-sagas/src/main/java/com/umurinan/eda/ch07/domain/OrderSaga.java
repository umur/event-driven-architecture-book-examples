package com.umurinan.eda.ch07.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/**
 * Persistent state machine for one order's SAGA execution.
 *
 * The id doubles as the orderId so the orchestrator can look sagas up by the
 * business key that flows through every command and reply.
 *
 * <h3>Concurrency safety</h3>
 * <p>{@code version} enables JPA optimistic locking. The timeout handler (scheduler
 * thread) and the reply listeners (Kafka listener threads) both write this entity.
 * Without optimistic locking a late-arriving reply can overwrite a FAILED state
 * written by the timeout handler, or vice-versa, leaving the saga in an impossible
 * state. With {@code @Version}, the second writer gets an
 * {@link org.springframework.orm.ObjectOptimisticLockingFailureException} and the
 * winning write is preserved.
 *
 * <h3>Compensation tracking</h3>
 * <p>{@code issuedCompensations} records which compensation steps have already been
 * dispatched. Each service that participates in a SAGA must persist a record of its
 * compensation intent BEFORE emitting a command or event. If the consumer crashes
 * mid-saga, re-loading this set tells the orchestrator which compensations have
 * already been issued and prevents duplicate refunds or double-releases.
 */
@Entity
@Table(name = "order_sagas")
public class OrderSaga {

    @Id
    private String id;

    /**
     * Optimistic locking token. JPA increments this on every successful write.
     * A concurrent write with a stale token throws ObjectOptimisticLockingFailureException
     * rather than silently overwriting the winning update.
     */
    @Version
    private Long version;

    @Column(nullable = false)
    private String state;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Column
    private String paymentTransactionId;

    @Column
    private String inventoryReservationId;

    /**
     * Names of compensation steps that have already been dispatched (e.g.
     * {@code "REFUND_PAYMENT"}, {@code "RELEASE_INVENTORY"}). Persisting these
     * before emitting the compensating command means a crash-and-restart cannot
     * lose the record that compensation was already requested.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "order_saga_compensations", joinColumns = @JoinColumn(name = "saga_id"))
    @Column(name = "compensation_step")
    private Set<String> issuedCompensations = new HashSet<>();

    /** Required by JPA. */
    protected OrderSaga() {}

    /**
     * Creates a new saga in PAYMENT_PROCESSING state.
     * The saga id is the same as the orderId so commands and replies share a
     * common key throughout the workflow.
     */
    public OrderSaga(String orderId) {
        this.id = orderId;
        this.state = SagaState.PAYMENT_PROCESSING.name();
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public String getId() { return id; }

    public Long getVersion() { return version; }

    public String getState() { return state; }

    public void setState(String state) {
        this.state = state;
        this.updatedAt = Instant.now();
    }

    public Instant getCreatedAt() { return createdAt; }

    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }

    public String getPaymentTransactionId() { return paymentTransactionId; }

    public void setPaymentTransactionId(String paymentTransactionId) {
        this.paymentTransactionId = paymentTransactionId;
    }

    public String getInventoryReservationId() { return inventoryReservationId; }

    public void setInventoryReservationId(String inventoryReservationId) {
        this.inventoryReservationId = inventoryReservationId;
    }

    /**
     * Returns an unmodifiable view of all compensation steps already dispatched.
     * Use {@link #recordCompensation(String)} to add a step.
     */
    public Set<String> getIssuedCompensations() {
        return Set.copyOf(issuedCompensations);
    }

    /**
     * Records that a compensation step has been dispatched and persists the intent.
     * Call this BEFORE sending the compensating command so that a crash between the
     * save and the send does not leave the step un-tracked.
     *
     * @param step a stable, human-readable name such as {@code "REFUND_PAYMENT"}
     * @return {@code true} if the step was not already recorded (first time), {@code false} if it was a duplicate
     */
    public boolean recordCompensation(String step) {
        return issuedCompensations.add(step);
    }

    /**
     * Returns {@code true} if the named compensation step has already been dispatched.
     * Use this as an idempotency guard before re-sending a compensating command.
     */
    public boolean hasIssuedCompensation(String step) {
        return issuedCompensations.contains(step);
    }
}
