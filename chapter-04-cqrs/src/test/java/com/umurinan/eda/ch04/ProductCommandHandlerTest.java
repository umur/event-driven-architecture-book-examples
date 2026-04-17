package com.umurinan.eda.ch04;

import com.umurinan.eda.ch04.commands.CreateProductCommand;
import com.umurinan.eda.ch04.commands.UpdatePriceCommand;
import com.umurinan.eda.ch04.domain.Product;
import com.umurinan.eda.ch04.domain.ProductRepository;
import com.umurinan.eda.ch04.events.ProductCreated;
import com.umurinan.eda.ch04.events.ProductDeactivated;
import com.umurinan.eda.ch04.events.ProductPriceUpdated;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductCommandHandler")
class ProductCommandHandlerTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    private ProductCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ProductCommandHandler(productRepository, kafkaTemplate);
        when(kafkaTemplate.send(any(String.class), any(String.class), any()))
                .thenReturn(CompletableFuture.completedFuture(null));
    }

    @Test
    @DisplayName("createProduct() saves the Product to the write repository")
    void createProduct_savesProduct() {
        var command = new CreateProductCommand("p-1", "seller-1", "Acme Corp", "Widget", new BigDecimal("29.99"));

        handler.createProduct(command);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo("p-1");
        assertThat(captor.getValue().getName()).isEqualTo("Widget");
    }

    @Test
    @DisplayName("createProduct() publishes ProductCreated to 'product-created' with productId as key")
    void createProduct_publishesProductCreatedEvent() {
        var command = new CreateProductCommand("p-2", "seller-2", "Beta Ltd", "Gadget", new BigDecimal("49.99"));

        handler.createProduct(command);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<ProductCreated> eventCaptor = ArgumentCaptor.forClass(ProductCreated.class);
        verify(kafkaTemplate).send(eq("product-created"), eq("p-2"), eventCaptor.capture());

        var event = eventCaptor.getValue();
        assertThat(event.productId()).isEqualTo("p-2");
        assertThat(event.name()).isEqualTo("Gadget");
        assertThat(event.sellerName()).isEqualTo("Beta Ltd");
        assertThat(event.price()).isEqualByComparingTo(new BigDecimal("49.99"));
    }

    @Test
    @DisplayName("updatePrice() updates the price on the Product and publishes ProductPriceUpdated")
    void updatePrice_updatesWriteModelAndPublishesEvent() {
        var existingProduct = new Product("p-3", "seller-3", "Gamma Inc", "Thingamajig", new BigDecimal("10.00"));
        when(productRepository.findById("p-3")).thenReturn(Optional.of(existingProduct));

        var command = new UpdatePriceCommand("p-3", new BigDecimal("15.00"));
        handler.updatePrice(command);

        assertThat(existingProduct.getPrice()).isEqualByComparingTo(new BigDecimal("15.00"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<ProductPriceUpdated> eventCaptor = ArgumentCaptor.forClass(ProductPriceUpdated.class);
        verify(kafkaTemplate).send(eq("product-price-updated"), eq("p-3"), eventCaptor.capture());

        assertThat(eventCaptor.getValue().productId()).isEqualTo("p-3");
        assertThat(eventCaptor.getValue().newPrice()).isEqualByComparingTo(new BigDecimal("15.00"));
    }

    @Test
    @DisplayName("deactivateProduct() sets status to DEACTIVATED and publishes ProductDeactivated")
    void deactivateProduct_setsStatusAndPublishesEvent() {
        var existingProduct = new Product("p-4", "seller-4", "Delta Co", "Doohickey", new BigDecimal("5.00"));
        when(productRepository.findById("p-4")).thenReturn(Optional.of(existingProduct));

        handler.deactivateProduct("p-4");

        assertThat(existingProduct.getStatus()).isEqualTo("DEACTIVATED");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<ProductDeactivated> eventCaptor = ArgumentCaptor.forClass(ProductDeactivated.class);
        verify(kafkaTemplate).send(eq("product-deactivated"), eq("p-4"), eventCaptor.capture());

        assertThat(eventCaptor.getValue().productId()).isEqualTo("p-4");
    }
}
