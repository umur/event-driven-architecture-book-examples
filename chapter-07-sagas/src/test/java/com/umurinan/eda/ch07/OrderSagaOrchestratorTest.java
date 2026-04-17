package com.umurinan.eda.ch07;

import com.umurinan.eda.ch07.commands.ProcessPaymentCommand;
import com.umurinan.eda.ch07.commands.RefundPaymentCommand;
import com.umurinan.eda.ch07.commands.ReserveInventoryCommand;
import com.umurinan.eda.ch07.commands.ScheduleShipmentCommand;
import com.umurinan.eda.ch07.domain.OrderSaga;
import com.umurinan.eda.ch07.domain.OrderSagaRepository;
import com.umurinan.eda.ch07.domain.SagaState;
import com.umurinan.eda.ch07.replies.InventoryReply;
import com.umurinan.eda.ch07.replies.PaymentReply;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderSagaOrchestrator")
class OrderSagaOrchestratorTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Mock
    private OrderSagaRepository repository;

    private OrderSagaOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        orchestrator = new OrderSagaOrchestrator(kafkaTemplate, repository);
    }

    /**
     * Stubs kafkaTemplate.send() to return a completed future.
     * Called only inside tests that exercise a code path that publishes a command.
     * Tests asserting that NO command is sent must NOT call this — Mockito strict
     * stubbing would flag the unused stub as an error.
     */
    private void stubKafkaSend() {
        when(kafkaTemplate.send(any(String.class), any(String.class), any()))
                .thenReturn(CompletableFuture.completedFuture(null));
    }

    // -------------------------------------------------------------------------
    // startSaga
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("startSaga() saves the saga with state PAYMENT_PROCESSING")
    void startSaga_savesSagaWithPaymentProcessingState() {
        stubKafkaSend();
        when(repository.save(any(OrderSaga.class))).thenAnswer(inv -> inv.getArgument(0));

        orchestrator.startSaga("order-1", new BigDecimal("99.00"));

        var captor = ArgumentCaptor.forClass(OrderSaga.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getState()).isEqualTo(SagaState.PAYMENT_PROCESSING.name());
    }

    @Test
    @DisplayName("startSaga() publishes ProcessPaymentCommand to 'payment-commands'")
    void startSaga_publishesProcessPaymentCommand() {
        stubKafkaSend();
        when(repository.save(any(OrderSaga.class))).thenAnswer(inv -> inv.getArgument(0));

        orchestrator.startSaga("order-1", new BigDecimal("99.00"));

        verify(kafkaTemplate).send(eq("payment-commands"), eq("order-1"), any(ProcessPaymentCommand.class));
    }

    @Test
    @DisplayName("startSaga() embeds the orderId and amount in ProcessPaymentCommand")
    void startSaga_commandCarriesCorrectOrderIdAndAmount() {
        stubKafkaSend();
        when(repository.save(any(OrderSaga.class))).thenAnswer(inv -> inv.getArgument(0));

        orchestrator.startSaga("order-42", new BigDecimal("250.00"));

        var captor = ArgumentCaptor.forClass(ProcessPaymentCommand.class);
        verify(kafkaTemplate).send(eq("payment-commands"), eq("order-42"), captor.capture());
        assertThat(captor.getValue().orderId()).isEqualTo("order-42");
        assertThat(captor.getValue().amount()).isEqualByComparingTo(new BigDecimal("250.00"));
    }

    @Test
    @DisplayName("startSaga() sets a non-null idempotencyKey on ProcessPaymentCommand")
    void startSaga_commandCarriesNonNullIdempotencyKey() {
        stubKafkaSend();
        when(repository.save(any(OrderSaga.class))).thenAnswer(inv -> inv.getArgument(0));

        orchestrator.startSaga("order-1", new BigDecimal("99.00"));

        var captor = ArgumentCaptor.forClass(ProcessPaymentCommand.class);
        verify(kafkaTemplate).send(eq("payment-commands"), eq("order-1"), captor.capture());
        assertThat(captor.getValue().idempotencyKey()).isNotNull();
        assertThat(captor.getValue().idempotencyKey()).isInstanceOf(UUID.class);
    }

    // -------------------------------------------------------------------------
    // handlePaymentReply — success path
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("handlePaymentReply() with success transitions saga to INVENTORY_RESERVING")
    void handlePaymentReply_success_transitionsToInventoryReserving() {
        stubKafkaSend();
        var saga = new OrderSaga("order-1");
        when(repository.findById("order-1")).thenReturn(Optional.of(saga));
        when(repository.save(any(OrderSaga.class))).thenAnswer(inv -> inv.getArgument(0));

        var reply = new PaymentReply("order-1", true, "tx-abc", null);
        var ack = mock(Acknowledgment.class);

        orchestrator.handlePaymentReply(reply, ack);

        assertThat(saga.getState()).isEqualTo(SagaState.INVENTORY_RESERVING.name());
        verify(ack).acknowledge();
    }

    @Test
    @DisplayName("handlePaymentReply() with success publishes ReserveInventoryCommand to 'inventory-commands'")
    void handlePaymentReply_success_publishesReserveInventoryCommand() {
        stubKafkaSend();
        var saga = new OrderSaga("order-1");
        when(repository.findById("order-1")).thenReturn(Optional.of(saga));
        when(repository.save(any(OrderSaga.class))).thenAnswer(inv -> inv.getArgument(0));

        var reply = new PaymentReply("order-1", true, "tx-abc", null);
        var ack = mock(Acknowledgment.class);

        orchestrator.handlePaymentReply(reply, ack);

        verify(kafkaTemplate).send(eq("inventory-commands"), eq("order-1"), any(ReserveInventoryCommand.class));
    }

    @Test
    @DisplayName("handlePaymentReply() with success stores the transactionId on the saga")
    void handlePaymentReply_success_storesTransactionId() {
        stubKafkaSend();
        var saga = new OrderSaga("order-1");
        when(repository.findById("order-1")).thenReturn(Optional.of(saga));
        when(repository.save(any(OrderSaga.class))).thenAnswer(inv -> inv.getArgument(0));

        var reply = new PaymentReply("order-1", true, "tx-xyz", null);
        var ack = mock(Acknowledgment.class);

        orchestrator.handlePaymentReply(reply, ack);

        assertThat(saga.getPaymentTransactionId()).isEqualTo("tx-xyz");
    }

    @Test
    @DisplayName("handlePaymentReply() with success sets a non-null idempotencyKey on ReserveInventoryCommand")
    void handlePaymentReply_success_commandCarriesNonNullIdempotencyKey() {
        stubKafkaSend();
        var saga = new OrderSaga("order-1");
        when(repository.findById("order-1")).thenReturn(Optional.of(saga));
        when(repository.save(any(OrderSaga.class))).thenAnswer(inv -> inv.getArgument(0));

        var reply = new PaymentReply("order-1", true, "tx-abc", null);
        var ack = mock(Acknowledgment.class);

        orchestrator.handlePaymentReply(reply, ack);

        var captor = ArgumentCaptor.forClass(ReserveInventoryCommand.class);
        verify(kafkaTemplate).send(eq("inventory-commands"), eq("order-1"), captor.capture());
        assertThat(captor.getValue().idempotencyKey()).isNotNull();
        assertThat(captor.getValue().idempotencyKey()).isInstanceOf(UUID.class);
    }

    // -------------------------------------------------------------------------
    // handlePaymentReply — failure path
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("handlePaymentReply() with failure transitions saga to FAILED")
    void handlePaymentReply_failure_transitionsToFailed() {
        // No stubKafkaSend() — the failure path must NOT publish any command.
        var saga = new OrderSaga("order-1");
        when(repository.findById("order-1")).thenReturn(Optional.of(saga));
        when(repository.save(any(OrderSaga.class))).thenAnswer(inv -> inv.getArgument(0));

        var reply = new PaymentReply("order-1", false, null, "insufficient funds");
        var ack = mock(Acknowledgment.class);

        orchestrator.handlePaymentReply(reply, ack);

        assertThat(saga.getState()).isEqualTo(SagaState.FAILED.name());
        verify(ack).acknowledge();
    }

    @Test
    @DisplayName("handlePaymentReply() with failure does not publish any command")
    void handlePaymentReply_failure_doesNotPublishAnyCommand() {
        // No stubKafkaSend() — Mockito strict mode would flag it as unnecessary if added.
        var saga = new OrderSaga("order-1");
        when(repository.findById("order-1")).thenReturn(Optional.of(saga));
        when(repository.save(any(OrderSaga.class))).thenAnswer(inv -> inv.getArgument(0));

        var reply = new PaymentReply("order-1", false, null, "card declined");
        var ack = mock(Acknowledgment.class);

        orchestrator.handlePaymentReply(reply, ack);

        verifyNoInteractions(kafkaTemplate);
    }

    // -------------------------------------------------------------------------
    // handleInventoryReply — success path
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("handleInventoryReply() with success transitions saga to SHIPMENT_SCHEDULING")
    void handleInventoryReply_success_transitionsToShipmentScheduling() {
        stubKafkaSend();
        var saga = new OrderSaga("order-1");
        saga.setPaymentTransactionId("tx-abc");
        when(repository.findById("order-1")).thenReturn(Optional.of(saga));
        when(repository.save(any(OrderSaga.class))).thenAnswer(inv -> inv.getArgument(0));

        var reply = new InventoryReply("order-1", true, "res-001", null);
        var ack = mock(Acknowledgment.class);

        orchestrator.handleInventoryReply(reply, ack);

        assertThat(saga.getState()).isEqualTo(SagaState.SHIPMENT_SCHEDULING.name());
        verify(ack).acknowledge();
    }

    @Test
    @DisplayName("handleInventoryReply() with success publishes ScheduleShipmentCommand to 'shipment-commands'")
    void handleInventoryReply_success_publishesScheduleShipmentCommand() {
        stubKafkaSend();
        var saga = new OrderSaga("order-1");
        saga.setPaymentTransactionId("tx-abc");
        when(repository.findById("order-1")).thenReturn(Optional.of(saga));
        when(repository.save(any(OrderSaga.class))).thenAnswer(inv -> inv.getArgument(0));

        var reply = new InventoryReply("order-1", true, "res-001", null);
        var ack = mock(Acknowledgment.class);

        orchestrator.handleInventoryReply(reply, ack);

        verify(kafkaTemplate).send(eq("shipment-commands"), eq("order-1"), any(ScheduleShipmentCommand.class));
    }

    @Test
    @DisplayName("handleInventoryReply() with success stores the reservationId on the saga")
    void handleInventoryReply_success_storesReservationId() {
        stubKafkaSend();
        var saga = new OrderSaga("order-1");
        saga.setPaymentTransactionId("tx-abc");
        when(repository.findById("order-1")).thenReturn(Optional.of(saga));
        when(repository.save(any(OrderSaga.class))).thenAnswer(inv -> inv.getArgument(0));

        var reply = new InventoryReply("order-1", true, "res-007", null);
        var ack = mock(Acknowledgment.class);

        orchestrator.handleInventoryReply(reply, ack);

        assertThat(saga.getInventoryReservationId()).isEqualTo("res-007");
    }

    @Test
    @DisplayName("handleInventoryReply() with success sets a non-null idempotencyKey on ScheduleShipmentCommand")
    void handleInventoryReply_success_commandCarriesNonNullIdempotencyKey() {
        stubKafkaSend();
        var saga = new OrderSaga("order-1");
        saga.setPaymentTransactionId("tx-abc");
        when(repository.findById("order-1")).thenReturn(Optional.of(saga));
        when(repository.save(any(OrderSaga.class))).thenAnswer(inv -> inv.getArgument(0));

        var reply = new InventoryReply("order-1", true, "res-001", null);
        var ack = mock(Acknowledgment.class);

        orchestrator.handleInventoryReply(reply, ack);

        var captor = ArgumentCaptor.forClass(ScheduleShipmentCommand.class);
        verify(kafkaTemplate).send(eq("shipment-commands"), eq("order-1"), captor.capture());
        assertThat(captor.getValue().idempotencyKey()).isNotNull();
        assertThat(captor.getValue().idempotencyKey()).isInstanceOf(UUID.class);
    }

    // -------------------------------------------------------------------------
    // handleInventoryReply — failure path
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("handleInventoryReply() with failure transitions saga to COMPENSATING")
    void handleInventoryReply_failure_transitionsToCompensating() {
        stubKafkaSend();
        var saga = new OrderSaga("order-1");
        saga.setPaymentTransactionId("tx-abc");
        when(repository.findById("order-1")).thenReturn(Optional.of(saga));
        when(repository.save(any(OrderSaga.class))).thenAnswer(inv -> inv.getArgument(0));

        var reply = new InventoryReply("order-1", false, null, "out of stock");
        var ack = mock(Acknowledgment.class);

        orchestrator.handleInventoryReply(reply, ack);

        assertThat(saga.getState()).isEqualTo(SagaState.COMPENSATING.name());
        verify(ack).acknowledge();
    }

    @Test
    @DisplayName("handleInventoryReply() with failure publishes RefundPaymentCommand to 'payment-commands'")
    void handleInventoryReply_failure_publishesRefundPaymentCommand() {
        stubKafkaSend();
        var saga = new OrderSaga("order-1");
        saga.setPaymentTransactionId("tx-abc");
        when(repository.findById("order-1")).thenReturn(Optional.of(saga));
        when(repository.save(any(OrderSaga.class))).thenAnswer(inv -> inv.getArgument(0));

        var reply = new InventoryReply("order-1", false, null, "out of stock");
        var ack = mock(Acknowledgment.class);

        orchestrator.handleInventoryReply(reply, ack);

        var captor = ArgumentCaptor.forClass(RefundPaymentCommand.class);
        verify(kafkaTemplate).send(eq("payment-commands"), eq("order-1"), captor.capture());
        assertThat(captor.getValue().transactionId()).isEqualTo("tx-abc");
    }

    @Test
    @DisplayName("handleInventoryReply() with failure sets a non-null idempotencyKey on RefundPaymentCommand")
    void handleInventoryReply_failure_commandCarriesNonNullIdempotencyKey() {
        stubKafkaSend();
        var saga = new OrderSaga("order-1");
        saga.setPaymentTransactionId("tx-abc");
        when(repository.findById("order-1")).thenReturn(Optional.of(saga));
        when(repository.save(any(OrderSaga.class))).thenAnswer(inv -> inv.getArgument(0));

        var reply = new InventoryReply("order-1", false, null, "out of stock");
        var ack = mock(Acknowledgment.class);

        orchestrator.handleInventoryReply(reply, ack);

        var captor = ArgumentCaptor.forClass(RefundPaymentCommand.class);
        verify(kafkaTemplate).send(eq("payment-commands"), eq("order-1"), captor.capture());
        assertThat(captor.getValue().idempotencyKey()).isNotNull();
        assertThat(captor.getValue().idempotencyKey()).isInstanceOf(UUID.class);
    }
}
