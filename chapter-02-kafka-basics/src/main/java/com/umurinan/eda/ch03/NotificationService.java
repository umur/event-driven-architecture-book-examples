package com.umurinan.eda.ch03;

import com.umurinan.eda.ch03.events.OrderPlaced;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    @KafkaListener(topics = "order-placed", groupId = "notification-service")
    public void onOrderPlaced(OrderPlaced event, Acknowledgment ack) {
        if (event.orderId() == null) {
            throw new RuntimeException("orderId must not be null — cannot send notification");
        }

        log.info("Sending order confirmation to customerId={} for orderId={} total={}",
                event.customerId(), event.orderId(), event.total());

        // Stubbed email send — in production this would call an EmailClient
        sendConfirmationEmail(event);

        ack.acknowledge();
    }

    private void sendConfirmationEmail(OrderPlaced event) {
        log.info("[EMAIL] Order {} confirmed for customer {}. Total: {}",
                event.orderId(), event.customerId(), event.total());
    }
}
