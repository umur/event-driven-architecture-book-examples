package com.umurinan.eda.ch06.readmodel;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CustomerOrderHistoryRepository extends JpaRepository<CustomerOrderHistory, Long> {

    List<CustomerOrderHistory> findByCustomerId(String customerId);
}
