package com.umurinan.eda.ch04;

import com.umurinan.eda.ch04.domain.ProductListing;
import com.umurinan.eda.ch04.domain.ProductListingRepository;
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
import org.springframework.kafka.support.Acknowledgment;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductProjection")
class ProductProjectionTest {

    @Mock
    private ProductListingRepository productListingRepository;

    @Mock
    private Acknowledgment ack;

    private ProductProjection projection;

    @BeforeEach
    void setUp() {
        projection = new ProductProjection(productListingRepository);
    }

    @Test
    @DisplayName("onProductCreated() saves a new ProductListing with correct name, price, and sellerName")
    void onProductCreated_savesProductListing() {
        var event = new ProductCreated("p-10", "s-1", "Omega Store", "Notebook", new BigDecimal("12.50"));

        projection.onProductCreated(event, ack);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<ProductListing> captor = ArgumentCaptor.forClass(ProductListing.class);
        verify(productListingRepository).save(captor.capture());

        var listing = captor.getValue();
        assertThat(listing.getId()).isEqualTo("p-10");
        assertThat(listing.getName()).isEqualTo("Notebook");
        assertThat(listing.getPrice()).isEqualByComparingTo(new BigDecimal("12.50"));
        assertThat(listing.getSellerName()).isEqualTo("Omega Store");
        assertThat(listing.getStatus()).isEqualTo("ACTIVE");

        verify(ack).acknowledge();
    }

    @Test
    @DisplayName("onProductPriceUpdated() updates the price on the existing ProductListing")
    void onProductPriceUpdated_updatesPriceOnExistingListing() {
        var existing = new ProductListing("p-11", "Pen", new BigDecimal("3.00"), "Sigma Shop");
        when(productListingRepository.findById("p-11")).thenReturn(Optional.of(existing));

        var event = new ProductPriceUpdated("p-11", new BigDecimal("4.50"));

        projection.onProductPriceUpdated(event, ack);

        assertThat(existing.getPrice()).isEqualByComparingTo(new BigDecimal("4.50"));
        verify(productListingRepository).save(existing);
        verify(ack).acknowledge();
    }

    @Test
    @DisplayName("onProductDeactivated() sets the status to DEACTIVATED on the existing ProductListing")
    void onProductDeactivated_setsStatusToDeactivated() {
        var existing = new ProductListing("p-12", "Eraser", new BigDecimal("1.00"), "Tau Traders");
        when(productListingRepository.findById("p-12")).thenReturn(Optional.of(existing));

        var event = new ProductDeactivated("p-12");

        projection.onProductDeactivated(event, ack);

        assertThat(existing.getStatus()).isEqualTo("DEACTIVATED");
        verify(productListingRepository).save(existing);
        verify(ack).acknowledge();
    }
}
