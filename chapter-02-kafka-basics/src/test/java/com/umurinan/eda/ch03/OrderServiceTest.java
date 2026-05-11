package com.umurinan.eda.ch03;

import com.umurinan.eda.ch03.commands.PlaceOrderCommand;
import com.umurinan.eda.ch03.events.OrderPlaced;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderService")
class OrderServiceTest {

    @Mock
    private KafkaTemplate<String, OrderPlaced> kafkaTemplate;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(kafkaTemplate);
        // Return a completed future so the whenComplete callback doesn't NPE
        when(kafkaTemplate.send(any(String.class), any(String.class), any(OrderPlaced.class)))
                .thenReturn(CompletableFuture.completedFuture(null));
    }

    @Test
    @DisplayName("placeOrder() sends the event to the 'order-placed' topic")
    void placeOrder_sendsToOrderPlacedTopic() {
        var command = new PlaceOrderCommand("order-1", "customer-1", new BigDecimal("99.99"));

        orderService.placeOrder(command);

        verify(kafkaTemplate).send(eq("order-placed"), any(String.class), any(OrderPlaced.class));
    }

    @Test
    @DisplayName("placeOrder() uses the orderId as the Kafka message key")
    void placeOrder_usesOrderIdAsMessageKey() {
        var command = new PlaceOrderCommand("order-42", "customer-7", new BigDecimal("149.00"));

        orderService.placeOrder(command);

        verify(kafkaTemplate).send(eq("order-placed"), eq("order-42"), any(OrderPlaced.class));
    }

    @Test
    @DisplayName("placeOrder() returns a PlacedOrderResult with the correct orderId and total")
    void placeOrder_returnsResultWithCorrectOrderIdAndTotal() {
        var command = new PlaceOrderCommand("order-7", "customer-3", new BigDecimal("250.00"));

        var result = orderService.placeOrder(command);

        assertThat(result.orderId()).isEqualTo("order-7");
        assertThat(result.total()).isEqualByComparingTo(new BigDecimal("250.00"));
    }

    @Test
    @DisplayName("placeOrder() sets a non-null placedAt timestamp on the result")
    void placeOrder_setsNonNullPlacedAt() {
        var command = new PlaceOrderCommand("order-99", "customer-5", new BigDecimal("19.99"));

        var result = orderService.placeOrder(command);

        assertThat(result.placedAt()).isNotNull();
    }

    @Test
    @DisplayName("placeOrder() embeds the same placedAt timestamp in the published event")
    void placeOrder_eventCarriesSamePlacedAtAsResult() {
        var command = new PlaceOrderCommand("order-55", "customer-9", new BigDecimal("75.00"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<OrderPlaced> eventCaptor = ArgumentCaptor.forClass(OrderPlaced.class);

        var result = orderService.placeOrder(command);

        verify(kafkaTemplate).send(eq("order-placed"), eq("order-55"), eventCaptor.capture());

        assertThat(eventCaptor.getValue().placedAt()).isEqualTo(result.placedAt());
    }
}
