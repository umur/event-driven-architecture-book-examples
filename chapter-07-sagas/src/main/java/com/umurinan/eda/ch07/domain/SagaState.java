package com.umurinan.eda.ch07.domain;

/**
 * Every order saga moves through these states exactly once, left to right,
 * unless a failure forces it into COMPENSATING before landing on FAILED.
 *
 * <pre>
 * PENDING
 *   └─► PAYMENT_PROCESSING ──(success)──► INVENTORY_RESERVING ──(success)──► SHIPMENT_SCHEDULING ──► COMPLETED
 *              │                                    │
 *         (failure)                           (failure)
 *              │                                    │
 *              ▼                                    ▼
 *           FAILED                           COMPENSATING ──► FAILED
 * </pre>
 */
public enum SagaState {
    PENDING,
    PAYMENT_PROCESSING,
    INVENTORY_RESERVING,
    SHIPMENT_SCHEDULING,
    COMPENSATING,
    COMPLETED,
    FAILED
}
