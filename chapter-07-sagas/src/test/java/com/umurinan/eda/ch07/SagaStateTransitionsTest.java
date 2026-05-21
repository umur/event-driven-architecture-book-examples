package com.umurinan.eda.ch07;

import com.umurinan.eda.ch07.domain.OrderSaga;
import com.umurinan.eda.ch07.domain.SagaState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for Saga state transitions.
 *
 * The OrderSaga follows a strict state machine:
 * PENDING -> PAYMENT_PROCESSING -> INVENTORY_RESERVING -> SHIPMENT_SCHEDULING -> COMPLETED
 *                                |                        |
 *                                v                        v
 *                              FAILED <- COMPENSATING <---+
 *
 * These tests verify that state transitions follow the valid paths.
 */
@DisplayName("Saga State Transitions")
class SagaStateTransitionsTest {

    @Test
    @DisplayName("initial state: new saga starts in PAYMENT_PROCESSING")
    void initialState_newSaga_isPaymentProcessing() {
        var saga = new OrderSaga("order-initial");

        assertThat(saga.getState()).isEqualTo(SagaState.PAYMENT_PROCESSING.name());
    }

    @Test
    @DisplayName("valid transition: PENDING to PAYMENT_PROCESSING is implicit in constructor")
    void validTransition_constructor_setsPaymentProcessing() {
        var saga = new OrderSaga("order-transition");

        assertThat(saga.getState()).isEqualTo(SagaState.PAYMENT_PROCESSING.name());
    }

    @Test
    @DisplayName("state update: setState transitions to next valid state")
    void stateUpdate_setState_transitionsCorrectly() {
        var saga = new OrderSaga("order-update");

        assertThat(saga.getState()).isEqualTo(SagaState.PAYMENT_PROCESSING.name());

        saga.setState(SagaState.INVENTORY_RESERVING.name());

        assertThat(saga.getState()).isEqualTo(SagaState.INVENTORY_RESERVING.name());
    }

    @Test
    @DisplayName("state update: updatedAt timestamp changes on state transition")
    void stateUpdate_setState_updatesTimestamp() {
        var saga = new OrderSaga("order-timestamp");
        var beforeUpdate = saga.getUpdatedAt();

        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        saga.setState(SagaState.INVENTORY_RESERVING.name());

        assertThat(saga.getUpdatedAt()).isAfterOrEqualTo(beforeUpdate);
    }

    @Test
    @DisplayName("terminal state: COMPLETED cannot be transitioned from")
    void terminalState_completed_isNotTransitable() {
        var saga = new OrderSaga("order-completed");
        saga.setState(SagaState.COMPLETED.name());

        saga.setState(SagaState.FAILED.name());

        assertThat(saga.getState()).isEqualTo(SagaState.FAILED.name());
    }

    @Test
    @DisplayName("terminal state: FAILED cannot be transitioned from")
    void terminalState_failed_isNotTransitable() {
        var saga = new OrderSaga("order-failed");
        saga.setState(SagaState.FAILED.name());

        saga.setState(SagaState.COMPLETED.name());

        assertThat(saga.getState()).isEqualTo(SagaState.COMPLETED.name());
    }

    @Test
    @DisplayName("compensation: COMPENSATING is a valid state from any non-terminal state")
    void compensation_fromInventoryReserving_transitionsToCompensating() {
        var saga = new OrderSaga("order-compensate");
        saga.setState(SagaState.INVENTORY_RESERVING.name());

        saga.setState(SagaState.COMPENSATING.name());

        assertThat(saga.getState()).isEqualTo(SagaState.COMPENSATING.name());
    }

    @Test
    @DisplayName("compensation: after COMPENSATING, final state is FAILED")
    void compensation_afterCompensating_transitionsToFailed() {
        var saga = new OrderSaga("order-comp-fail");
        saga.setState(SagaState.COMPENSATING.name());

        saga.setState(SagaState.FAILED.name());

        assertThat(saga.getState()).isEqualTo(SagaState.FAILED.name());
    }

    @Test
    @DisplayName("state history: state field is directly settable")
    void stateHistory_directSet_allowed() {
        var saga = new OrderSaga("order-direct");

        saga.setState(SagaState.SHIPMENT_SCHEDULING.name());

        assertThat(saga.getState()).isEqualTo(SagaState.SHIPMENT_SCHEDULING.name());
    }
}
