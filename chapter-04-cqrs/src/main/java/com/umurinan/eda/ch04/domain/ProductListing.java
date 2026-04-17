package com.umurinan.eda.ch04.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "product_listings")
public class ProductListing {

    @Id
    private String id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private BigDecimal price;

    @Column(nullable = false)
    private String sellerName;

    @Column(nullable = false)
    private String status = "ACTIVE";

    protected ProductListing() {}

    public ProductListing(String id, String name, BigDecimal price, String sellerName) {
        this.id = id;
        this.name = name;
        this.price = price;
        this.sellerName = sellerName;
        this.status = "ACTIVE";
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public BigDecimal getPrice() { return price; }
    public String getSellerName() { return sellerName; }
    public String getStatus() { return status; }

    public void setPrice(BigDecimal price) { this.price = price; }
    public void setStatus(String status) { this.status = status; }
}
