package com.umurinan.eda.ch07;

import com.umurinan.eda.ch07.domain.OrderSagaRepository;
import com.umurinan.eda.ch07.domain.SagaState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Periodic sweep that detects sagas stuck in PAYMENT_PROCESSING.
 *
 * A saga can get stuck if the payment service never replies — for example due to
 * a network partition or a crash in the participant. Rather than waiting forever,
 * this handler marks those sagas as FAILED after 30 seconds so downstream systems
 * (monitoring, customer support tooling) can react.
 *
 * <p>Scheduling is conditional on {@code scheduling.enabled=true} so integration
 * tests can disable it and drive timeouts manually.
 *
 * <h3>Concurrency note</h3>
 * <p>This handler runs on a scheduler thread while Kafka listener threads can
 * concurrently update the same {@code OrderSaga} rows. The {@code @Version} field
 * on {@code OrderSaga} enables optimistic locking: if a reply arrives and advances
 * the saga at the same instant the timeout fires, one of the two writers will get an
 * {@link ObjectOptimisticLockingFailureException}. When the timeout handler loses
 * that race, the exception is caught and logged at DEBUG level — this is expected,
 * normal behaviour, not an error.
 */
@Component
@ConditionalOnProperty(name = "scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class SagaTimeoutHandler {

    private static final Logger log = LoggerFactory.getLogger(SagaTimeoutHandler.class);

    private static final long TIMEOUT_SECONDS = 30L;

    private final OrderSagaRepository repository;

    public SagaTimeoutHandler(OrderSagaRepository repository) {
        this.repository = repository;
    }

    /**
     * Runs every 5 seconds. Finds every PAYMENT_PROCESSING saga whose
     * {@code createdAt} is older than 30 seconds and marks it FAILED.
     *
     * <p>Each saga is saved individually inside its own try-catch so that a
     * concurrent state transition on one saga (e.g. a reply arriving at T=30 s)
     * does not prevent the remaining stale sagas from being processed.
     */
    @Scheduled(fixedDelay = 5_000)
    public void checkForTimeouts() {
        var cutoff = Instant.now().minusSeconds(TIMEOUT_SECONDS);
        var stale  = repository.findByStateAndCreatedAtBefore(
                SagaState.PAYMENT_PROCESSING.name(), cutoff);

        for (var saga : stale) {
            try {
                log.warn("SAGA timeout detected sagaId={} createdAt={} — marking FAILED",
                        saga.getId(), saga.getCreatedAt());
                saga.setState(SagaState.FAILED.name());
                repository.save(saga);
            } catch (ObjectOptimisticLockingFailureException e) {
                // A Kafka listener thread updated this saga concurrently (e.g. a reply
                // arrived at the same instant the timeout fired). The listener's write
                // won. The saga is already in a valid terminal or next state — nothing
                // to do here.
                log.debug("SAGA timeout lost optimistic-lock race sagaId={} — " +
                          "a concurrent reply already advanced the state; skipping",
                        saga.getId());
            }
        }
    }
}
