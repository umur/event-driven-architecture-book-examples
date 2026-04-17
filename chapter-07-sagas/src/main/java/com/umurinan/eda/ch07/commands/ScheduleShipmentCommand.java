package com.umurinan.eda.ch07.commands;

import java.util.UUID;

/**
 * Sent to "shipment-commands" after inventory is reserved.
 * The shipment service replies on "shipment-replies" with a {@code ShipmentReply}.
 */
public record ScheduleShipmentCommand(
        String sagaId,
        String orderId,
        String reservationId,
        UUID idempotencyKey
) {}
