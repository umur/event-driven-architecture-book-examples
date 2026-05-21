package com.umurinan.eda.ch04;

import com.umurinan.eda.ch04.domain.ProductListing;
import com.umurinan.eda.ch04.domain.ProductListingRepository;
import com.umurinan.eda.ch04.events.ProductCreated;
import com.umurinan.eda.ch04.events.ProductDeactivated;
import com.umurinan.eda.ch04.events.ProductPriceUpdated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

@Service
public class ProductProjection {

    private static final Logger log = LoggerFactory.getLogger(ProductProjection.class);

    private final ProductListingRepository productListingRepository;

    public ProductProjection(ProductListingRepository productListingRepository) {
        this.productListingRepository = productListingRepository;
    }

    @KafkaListener(
            topics = "product-created",
            groupId = "product-projection",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onProductCreated(ProductCreated event, Acknowledgment ack) {
        var listing = new ProductListing(
                event.productId(),
                event.name(),
                event.price(),
                event.sellerName()
        );
        productListingRepository.save(listing);
        log.info("ProductListing created for productId={}", event.productId());
        ack.acknowledge();
    }

    @KafkaListener(
            topics = "product-price-updated",
            groupId = "product-projection",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onProductPriceUpdated(ProductPriceUpdated event, Acknowledgment ack) {
        productListingRepository.findById(event.productId()).ifPresent(listing -> {
            listing.setPrice(event.newPrice());
            productListingRepository.save(listing);
            log.info("ProductListing price updated for productId={} newPrice={}", event.productId(), event.newPrice());
        });
        ack.acknowledge();
    }

    @KafkaListener(
            topics = "product-deactivated",
            groupId = "product-projection",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onProductDeactivated(ProductDeactivated event, Acknowledgment ack) {
        productListingRepository.findById(event.productId()).ifPresent(listing -> {
            listing.setStatus("DEACTIVATED");
            productListingRepository.save(listing);
            log.info("ProductListing deactivated for productId={}", event.productId());
        });
        ack.acknowledge();
    }
}
