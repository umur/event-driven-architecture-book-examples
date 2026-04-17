package com.umurinan.eda.ch09.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String customerId;

    @Column(nullable = false)
    private String status = "PLACED";

    @Column(nullable = false)
    private Instant createdAt;

    protected Order() {}

    public Order(String customerId) {
        this.customerId = customerId;
        this.status = "PLACED";
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getCustomerId() { return customerId; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
}
