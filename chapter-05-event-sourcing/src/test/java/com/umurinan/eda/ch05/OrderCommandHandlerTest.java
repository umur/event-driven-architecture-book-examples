package com.umurinan.eda.ch05;

import com.umurinan.eda.ch05.commands.CancelOrderCommand;
import com.umurinan.eda.ch05.commands.PlaceOrderCommand;
import com.umurinan.eda.ch05.domain.events.OrderCancelledEvent;
import com.umurinan.eda.ch05.domain.events.OrderEvent;
import com.umurinan.eda.ch05.domain.events.OrderPlacedEvent;
import com.umurinan.eda.ch05.store.EventStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderCommandHandlerTest {

    @Mock
    private EventStore eventStore;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    private OrderCommandHandler commandHandler;

    @BeforeEach
    void setUp() {
        commandHandler = new OrderCommandHandler(eventStore, kafkaTemplate);
    }

    @Test
    void placeOrder_appendsOrderPlacedEventToEventStore() {
        var command = new PlaceOrderCommand("order-1", "cust-1", new BigDecimal("99.99"));

        commandHandler.placeOrder(command);

        var eventCaptor = ArgumentCaptor.forClass(OrderEvent.class);
        verify(eventStore).append(eq("order-1"), eventCaptor.capture(), eq(0L));

        assertThat(eventCaptor.getValue()).isInstanceOf(OrderPlacedEvent.class);
        var placed = (OrderPlacedEvent) eventCaptor.getValue();
        assertThat(placed.orderId()).isEqualTo("order-1");
        assertThat(placed.customerId()).isEqualTo("cust-1");
        assertThat(placed.total()).isEqualByComparingTo(new BigDecimal("99.99"));
    }

    @Test
    void placeOrder_publishesOrderPlacedEventToOrderPlacedTopicWithOrderIdAsKey() {
        var command = new PlaceOrderCommand("order-2", "cust-2", new BigDecimal("199.00"));

        commandHandler.placeOrder(command);

        var eventCaptor = ArgumentCaptor.forClass(OrderEvent.class);
        verify(kafkaTemplate).send(eq("order-placed"), eq("order-2"), eventCaptor.capture());

        assertThat(eventCaptor.getValue()).isInstanceOf(OrderPlacedEvent.class);
    }

    @Test
    void cancelOrder_loadsEventsRehydratesAggregateAndAppendsOrderCancelledEvent() {
        var orderId = "order-3";
        var existingPlaced = new OrderPlacedEvent(
                orderId, "cust-3", new BigDecimal("50.00"), Instant.now().minusSeconds(60));
        when(eventStore.loadEvents(orderId)).thenReturn(List.of(existingPlaced));

        var command = new CancelOrderCommand(orderId, "not needed anymore");
        commandHandler.cancelOrder(command);

        var eventCaptor = ArgumentCaptor.forClass(OrderEvent.class);
        verify(eventStore).append(eq(orderId), eventCaptor.capture(), eq(1L));

        assertThat(eventCaptor.getValue()).isInstanceOf(OrderCancelledEvent.class);
        var cancelled = (OrderCancelledEvent) eventCaptor.getValue();
        assertThat(cancelled.orderId()).isEqualTo(orderId);
        assertThat(cancelled.reason()).isEqualTo("not needed anymore");
    }
}
