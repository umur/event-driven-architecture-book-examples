package com.umurinan.eda.ch07.replies;

/**
 * Published to "shipment-replies" by the shipment participant service.
 *
 * @param sagaId       correlates back to the {@code OrderSaga} id
 * @param success      true when the shipment was booked
 * @param errorMessage populated only on failure; human-readable reason
 */
public record ShipmentReply(
        String sagaId,
        boolean success,
        String errorMessage
) {}
