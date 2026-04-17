package com.umurinan.eda.ch06.readmodel;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "order_details")
public class OrderDetail {

    @Id
    @Column(name = "order_id")
    private String orderId;

    @Column(name = "customer_id", nullable = false)
    private String customerId;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal total;

    @Column(nullable = false)
    private String status;

    @Column(name = "tracking_number")
    private String trackingNumber;

    protected OrderDetail() {
    }

    public OrderDetail(String orderId, String customerId, BigDecimal total, String status) {
        this.orderId = orderId;
        this.customerId = customerId;
        this.total = total;
        this.status = status;
    }

    public String getOrderId() {
        return orderId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getTrackingNumber() {
        return trackingNumber;
    }

    public void setTrackingNumber(String trackingNumber) {
        this.trackingNumber = trackingNumber;
    }
}
