package com.umurinan.eda.ch06.readmodel;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "daily_order_summary")
public class DailyOrderSummary {

    @Id
    @Column(name = "summary_date")
    private LocalDate summaryDate;

    @Column(name = "order_count", nullable = false)
    private int orderCount;

    @Column(name = "total_revenue", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalRevenue;

    protected DailyOrderSummary() {
    }

    public DailyOrderSummary(LocalDate summaryDate) {
        this.summaryDate = summaryDate;
        this.orderCount = 0;
        this.totalRevenue = BigDecimal.ZERO;
    }

    public void incrementOrder(BigDecimal orderTotal) {
        this.orderCount++;
        this.totalRevenue = this.totalRevenue.add(orderTotal);
    }

    public LocalDate getSummaryDate() {
        return summaryDate;
    }

    public int getOrderCount() {
        return orderCount;
    }

    public BigDecimal getTotalRevenue() {
        return totalRevenue;
    }
}
