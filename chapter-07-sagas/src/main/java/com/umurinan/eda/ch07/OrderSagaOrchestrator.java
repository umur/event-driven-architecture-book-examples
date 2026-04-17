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
import com.umurinan.eda.ch07.replies.ShipmentReply;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Orchestration SAGA for the order workflow.
 *
 * The orchestrator owns the saga state machine. It sends commands to participant
 * services and reacts to their replies. All state is persisted to H2 after every
 * transition so the orchestrator can recover from a restart without losing progress.
 *
 * <p>Happy path:
 * <ol>
 *   <li>startSaga → ProcessPaymentCommand → payment-commands</li>
 *   <li>PaymentReply(success) → ReserveInventoryCommand → inventory-commands</li>
 *   <li>InventoryReply(success) → ScheduleShipmentCommand → shipment-commands</li>
 *   <li>ShipmentReply(success) → COMPLETED</li>
 * </ol>
 *
 * <p>Compensation path:
 * <ol>
 *   <li>InventoryReply(failure) → RefundPaymentCommand → payment-commands, state = COMPENSATING</li>
 *   <li>After refund the saga lands on FAILED</li>
 * </ol>
 */
@Service
public class OrderSagaOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(OrderSagaOrchestrator.class);

    static final String PAYMENT_COMMANDS  = "payment-commands";
    static final String INVENTORY_COMMANDS = "inventory-commands";
    static final String SHIPMENT_COMMANDS  = "shipment-commands";
    static final String PAYMENT_REPLIES   = "payment-replies";
    static final String INVENTORY_REPLIES = "inventory-replies";
    static final String SHIPMENT_REPLIES  = "shipment-replies";

    // Placeholder values used inside commands when the orchestrator does not
    // know the product details. A real system would look these up from the order.
    private static final String DEFAULT_PRODUCT_ID = "unknown";
    private static final int    DEFAULT_QUANTITY    = 1;

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final OrderSagaRepository repository;

    public OrderSagaOrchestrator(KafkaTemplate<String, Object> kafkaTemplate,
                                 OrderSagaRepository repository) {
        this.kafkaTemplate = kafkaTemplate;
        this.repository    = repository;
    }

    // -------------------------------------------------------------------------
    // Start
    // -------------------------------------------------------------------------

    /**
     * Initialises a new saga and fires the first command.
     *
     * @param orderId business key; also used as the saga id and Kafka message key
     * @param amount  charge amount forwarded to the payment service
     */
    public void startSaga(String orderId, BigDecimal amount) {
        var saga = new OrderSaga(orderId);
        repository.save(saga);

        var command = new ProcessPaymentCommand(orderId, orderId, amount, UUID.randomUUID());
        kafkaTemplate.send(PAYMENT_COMMANDS, orderId, command);

        log.info("SAGA started orderId={} state={}", orderId, saga.getState());
    }

    // -------------------------------------------------------------------------
    // Payment replies
    // -------------------------------------------------------------------------

    @KafkaListener(topics = PAYMENT_REPLIES, groupId = "order-saga-orchestrator")
    public void handlePaymentReply(PaymentReply reply, Acknowledgment ack) {
        var saga = load(reply.sagaId());

        if (reply.success()) {
            saga.setPaymentTransactionId(reply.transactionId());
            saga.setState(SagaState.INVENTORY_RESERVING.name());
            repository.save(saga);

            var command = new ReserveInventoryCommand(
                    saga.getId(), saga.getId(),
                    DEFAULT_PRODUCT_ID, DEFAULT_QUANTITY, UUID.randomUUID());
            kafkaTemplate.send(INVENTORY_COMMANDS, saga.getId(), command);

            log.info("SAGA payment succeeded sagaId={} txId={}", saga.getId(), reply.transactionId());
        } else {
            saga.setState(SagaState.FAILED.name());
            repository.save(saga);

            log.warn("SAGA payment failed sagaId={} reason={}", saga.getId(), reply.errorMessage());
        }

        ack.acknowledge();
    }

    // -------------------------------------------------------------------------
    // Inventory replies
    // -------------------------------------------------------------------------

    @KafkaListener(topics = INVENTORY_REPLIES, groupId = "order-saga-orchestrator")
    public void handleInventoryReply(InventoryReply reply, Acknowledgment ack) {
        var saga = load(reply.sagaId());

        if (reply.success()) {
            saga.setInventoryReservationId(reply.reservationId());
            saga.setState(SagaState.SHIPMENT_SCHEDULING.name());
            repository.save(saga);

            var command = new ScheduleShipmentCommand(
                    saga.getId(), saga.getId(), reply.reservationId(), UUID.randomUUID());
            kafkaTemplate.send(SHIPMENT_COMMANDS, saga.getId(), command);

            log.info("SAGA inventory reserved sagaId={} reservationId={}", saga.getId(), reply.reservationId());
        } else {
            saga.setState(SagaState.COMPENSATING.name());
            // Record the compensation intent BEFORE sending the command. If the
            // process crashes between the save and the send, re-loading the saga
            // reveals that REFUND_PAYMENT was already dispatched, preventing a
            // duplicate refund on recovery.
            saga.recordCompensation("REFUND_PAYMENT");
            repository.save(saga);

            // Compensate: refund the payment that was already captured.
            var refund = new RefundPaymentCommand(
                    saga.getId(), saga.getId(), saga.getPaymentTransactionId(), UUID.randomUUID());
            kafkaTemplate.send(PAYMENT_COMMANDS, saga.getId(), refund);

            log.warn("SAGA inventory failed sagaId={} — compensating, reason={}", saga.getId(), reply.errorMessage());
        }

        ack.acknowledge();
    }

    // -------------------------------------------------------------------------
    // Shipment replies
    // -------------------------------------------------------------------------

    @KafkaListener(topics = SHIPMENT_REPLIES, groupId = "order-saga-orchestrator")
    public void handleShipmentReply(ShipmentReply reply, Acknowledgment ack) {
        var saga = load(reply.sagaId());

        if (reply.success()) {
            saga.setState(SagaState.COMPLETED.name());
            log.info("SAGA completed sagaId={}", saga.getId());
        } else {
            saga.setState(SagaState.COMPENSATING.name());
            log.warn("SAGA shipment failed sagaId={} — marking COMPENSATING, reason={}", saga.getId(), reply.errorMessage());
        }

        repository.save(saga);
        ack.acknowledge();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private OrderSaga load(String sagaId) {
        return repository.findById(sagaId)
                .orElseThrow(() -> new IllegalStateException(
                        "No saga found for sagaId=" + sagaId));
    }
}
