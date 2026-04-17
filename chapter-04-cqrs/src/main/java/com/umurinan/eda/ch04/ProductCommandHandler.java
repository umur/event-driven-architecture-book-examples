package com.umurinan.eda.ch04;

import com.umurinan.eda.ch04.commands.CreateProductCommand;
import com.umurinan.eda.ch04.commands.UpdatePriceCommand;
import com.umurinan.eda.ch04.domain.Product;
import com.umurinan.eda.ch04.domain.ProductRepository;
import com.umurinan.eda.ch04.events.ProductCreated;
import com.umurinan.eda.ch04.events.ProductDeactivated;
import com.umurinan.eda.ch04.events.ProductPriceUpdated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductCommandHandler {

    private static final Logger log = LoggerFactory.getLogger(ProductCommandHandler.class);

    static final String PRODUCT_CREATED_TOPIC = "product-created";
    static final String PRODUCT_PRICE_UPDATED_TOPIC = "product-price-updated";
    static final String PRODUCT_DEACTIVATED_TOPIC = "product-deactivated";

    private final ProductRepository productRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public ProductCommandHandler(ProductRepository productRepository,
                                  KafkaTemplate<String, Object> kafkaTemplate) {
        this.productRepository = productRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Transactional
    public void createProduct(CreateProductCommand command) {
        var product = new Product(
                command.productId(),
                command.sellerId(),
                command.sellerName(),
                command.name(),
                command.price()
        );
        productRepository.save(product);

        var event = new ProductCreated(
                command.productId(),
                command.sellerId(),
                command.sellerName(),
                command.name(),
                command.price()
        );
        kafkaTemplate.send(PRODUCT_CREATED_TOPIC, command.productId(), event);
        log.info("Product created: productId={}", command.productId());
    }

    @Transactional
    public void updatePrice(UpdatePriceCommand command) {
        var product = productRepository.findById(command.productId())
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + command.productId()));

        product.setPrice(command.newPrice());
        productRepository.save(product);

        var event = new ProductPriceUpdated(command.productId(), command.newPrice());
        kafkaTemplate.send(PRODUCT_PRICE_UPDATED_TOPIC, command.productId(), event);
        log.info("Product price updated: productId={} newPrice={}", command.productId(), command.newPrice());
    }

    @Transactional
    public void deactivateProduct(String productId) {
        var product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + productId));

        product.setStatus("DEACTIVATED");
        productRepository.save(product);

        var event = new ProductDeactivated(productId);
        kafkaTemplate.send(PRODUCT_DEACTIVATED_TOPIC, productId, event);
        log.info("Product deactivated: productId={}", productId);
    }
}
