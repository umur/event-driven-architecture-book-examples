package com.umurinan.eda.ch07.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface OrderSagaRepository extends JpaRepository<OrderSaga, String> {

    /**
     * Returns all sagas in the given state whose {@code createdAt} timestamp
     * is strictly before {@code cutoff}. Used by the timeout handler to find
     * stuck sagas.
     */
    List<OrderSaga> findByStateAndCreatedAtBefore(String state, Instant cutoff);
}
