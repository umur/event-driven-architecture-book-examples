package com.umurinan.eda.ch07.replies;

/**
 * Published to "inventory-replies" by the inventory participant service.
 *
 * @param sagaId        correlates back to the {@code OrderSaga} id
 * @param success       true when stock was reserved
 * @param reservationId populated only on success; opaque token for the reservation
 * @param errorMessage  populated only on failure; human-readable reason
 */
public record InventoryReply(
        String sagaId,
        boolean success,
        String reservationId,
        String errorMessage
) {}
