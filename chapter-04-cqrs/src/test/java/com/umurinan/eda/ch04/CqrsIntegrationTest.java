package com.umurinan.eda.ch04;

import com.umurinan.eda.ch04.commands.CreateProductCommand;
import com.umurinan.eda.ch04.domain.ProductListingRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = {"product-created", "product-price-updated", "product-deactivated"})
@TestPropertySource(properties = {
        "spring.kafka.consumer.auto-offset-reset=earliest",
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}"
})
@DirtiesContext
@DisplayName("CQRS integration — write side propagates to read model via Kafka")
class CqrsIntegrationTest {

    @Autowired
    private ProductCommandHandler productCommandHandler;

    @Autowired
    private ProductListingRepository productListingRepository;

    @Test
    @DisplayName("createProduct() eventually populates the read model with correct name and price")
    void createProduct_eventuallyPopulatesReadModel() {
        var command = new CreateProductCommand(
                "p-integration-1",
                "seller-int-1",
                "Integration Store",
                "Test Widget",
                new BigDecimal("39.99")
        );

        productCommandHandler.createProduct(command);

        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    var listing = productListingRepository.findById("p-integration-1");
                    assertThat(listing).isPresent();
                    assertThat(listing.get().getName()).isEqualTo("Test Widget");
                    assertThat(listing.get().getPrice()).isEqualByComparingTo(new BigDecimal("39.99"));
                    assertThat(listing.get().getSellerName()).isEqualTo("Integration Store");
                });
    }

    @Test
    @DisplayName("updatePrice() eventually reflects the new price in the read model")
    void updatePrice_eventuallyUpdatesReadModel() {
        var createCommand = new CreateProductCommand(
                "p-integration-2",
                "seller-int-2",
                "Price Test Store",
                "Adjustable Gadget",
                new BigDecimal("20.00")
        );
        productCommandHandler.createProduct(createCommand);

        // Wait for the initial listing to appear before issuing the price update
        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() ->
                        assertThat(productListingRepository.findById("p-integration-2")).isPresent()
                );

        productCommandHandler.updatePrice(
                new com.umurinan.eda.ch04.commands.UpdatePriceCommand("p-integration-2", new BigDecimal("25.00"))
        );

        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    var listing = productListingRepository.findById("p-integration-2");
                    assertThat(listing).isPresent();
                    assertThat(listing.get().getPrice()).isEqualByComparingTo(new BigDecimal("25.00"));
                });
    }
}
