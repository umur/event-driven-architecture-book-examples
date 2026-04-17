package com.umurinan.eda.ch13;

import au.com.dius.pact.consumer.MessagePactBuilder;
import au.com.dius.pact.consumer.dsl.PactDslJsonBody;
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.consumer.junit5.ProviderType;
import au.com.dius.pact.core.model.PactSpecVersion;
import au.com.dius.pact.core.model.annotations.Pact;
import au.com.dius.pact.core.model.messaging.Message;
import au.com.dius.pact.core.model.messaging.MessagePact;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pattern: Consumer-Driven Contract Test (Pact async/messaging)
 *
 * This test lives on the consumer side (OrderService). It defines what the
 * consumer expects from the producer (PaymentService) and generates a Pact
 * contract file under target/pacts/.
 *
 * The producer team runs their own verification test against that contract
 * file. If the producer's event shape no longer satisfies the consumer's
 * expectations, the verification fails before deployment.
 *
 * Dependency required:
 *   au.com.dius.pact.consumer:junit5:<version>
 *
 * NOTE: This test uses @PactFolder("pacts") on the producer side, which
 * exchanges contract files via the local filesystem. This is suitable for
 * development and single-repo setups. In a CI pipeline where producer and
 * consumer live in separate repositories, replace @PactFolder with
 * @PactBroker (see PactProducerVerificationTest for the comment).
 */
@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = "PaymentService", providerType = ProviderType.ASYNCH)
class PactConsumerContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    // -------------------------------------------------------------------------
    // Contract definition
    // -------------------------------------------------------------------------

    /**
     * Defines the consumer's expectations for a "payment processed" event.
     * Pact writes the resulting contract to target/pacts/OrderService-PaymentService.json.
     */
    @Pact(consumer = "OrderService", provider = "PaymentService")
    public MessagePact paymentProcessedEventPact(MessagePactBuilder builder) {
        return builder
                .expectsToReceive("a payment processed event")
                .withContent(new PactDslJsonBody()
                        .stringType("orderId", "order-123")        // (1)
                        .decimalType("amount", 99.99)
                        .stringMatcher("status", "SUCCESS|FAILED", "SUCCESS"))
                .toPact();
    }

    // -------------------------------------------------------------------------
    // Consumer verification
    // -------------------------------------------------------------------------

    /**
     * Verifies that the consumer can deserialize and handle the event shape
     * defined in the Pact above.
     *
     * @param pact injected by the Pact extension from the method above
     */
    @Test
    @PactTestFor(pactMethod = "paymentProcessedEventPact")
    void consumerCanHandlePaymentProcessedEvent(MessagePact pact) throws Exception {
        Message message = pact.getMessages().get(0);

        // Deserialize the Pact-generated message body into the consumer's DTO
        @SuppressWarnings("unchecked")
        Map<String, Object> body = objectMapper.readValue(
                message.contentsAsString(), Map.class);

        // Assert the consumer can extract what it needs
        assertThat(body).containsKey("orderId");
        assertThat(body).containsKey("amount");
        assertThat(body.get("status")).isEqualTo("SUCCESS");
    }
}
// (1) stringType() asserts the field exists and is a string; the second
//     argument is the example value written into the contract JSON. The
//     producer only needs to match the type, not the exact value.
