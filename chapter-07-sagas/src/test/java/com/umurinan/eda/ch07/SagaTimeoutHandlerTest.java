package com.umurinan.eda.ch07;

import com.umurinan.eda.ch07.domain.OrderSaga;
import com.umurinan.eda.ch07.domain.OrderSagaRepository;
import com.umurinan.eda.ch07.domain.SagaState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SagaTimeoutHandler")
class SagaTimeoutHandlerTest {

    @Mock
    private OrderSagaRepository repository;

    private SagaTimeoutHandler handler;

    @BeforeEach
    void setUp() {
        handler = new SagaTimeoutHandler(repository);
    }

    @Test
    @DisplayName("checkForTimeouts() marks a PAYMENT_PROCESSING saga older than 30s as FAILED")
    void checkForTimeouts_marksExpiredSagaAsFailed() {
        var staleSaga = buildSagaWithAge("order-stale", SagaState.PAYMENT_PROCESSING, 35);
        when(repository.findByStateAndCreatedAtBefore(
                eq(SagaState.PAYMENT_PROCESSING.name()), any(Instant.class)))
                .thenReturn(List.of(staleSaga));

        handler.checkForTimeouts();

        assertThat(staleSaga.getState()).isEqualTo(SagaState.FAILED.name());
        verify(repository).save(staleSaga);
    }

    @Test
    @DisplayName("checkForTimeouts() does not mark a PAYMENT_PROCESSING saga created 10s ago as FAILED")
    void checkForTimeouts_doesNotMarkRecentSaga() {
        // The repository query filters by cutoff time — nothing is returned for a fresh saga.
        when(repository.findByStateAndCreatedAtBefore(
                eq(SagaState.PAYMENT_PROCESSING.name()), any(Instant.class)))
                .thenReturn(List.of());

        handler.checkForTimeouts();

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("checkForTimeouts() does not affect a saga in COMPLETED state")
    void checkForTimeouts_doesNotAffectCompletedSaga() {
        // A COMPLETED saga is never returned by the query (which filters on PAYMENT_PROCESSING).
        when(repository.findByStateAndCreatedAtBefore(
                eq(SagaState.PAYMENT_PROCESSING.name()), any(Instant.class)))
                .thenReturn(List.of());

        handler.checkForTimeouts();

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("checkForTimeouts() processes multiple timed-out sagas in one sweep")
    void checkForTimeouts_handlesMultipleSagas() {
        var saga1 = buildSagaWithAge("order-1", SagaState.PAYMENT_PROCESSING, 60);
        var saga2 = buildSagaWithAge("order-2", SagaState.PAYMENT_PROCESSING, 45);
        when(repository.findByStateAndCreatedAtBefore(
                eq(SagaState.PAYMENT_PROCESSING.name()), any(Instant.class)))
                .thenReturn(List.of(saga1, saga2));

        handler.checkForTimeouts();

        assertThat(saga1.getState()).isEqualTo(SagaState.FAILED.name());
        assertThat(saga2.getState()).isEqualTo(SagaState.FAILED.name());
        verify(repository, times(2)).save(any(OrderSaga.class));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private OrderSaga buildSagaWithAge(String orderId, SagaState state, long ageSeconds) {
        var saga = new OrderSaga(orderId);
        saga.setState(state.name());
        saga.setCreatedAt(Instant.now().minusSeconds(ageSeconds));
        return saga;
    }
}
