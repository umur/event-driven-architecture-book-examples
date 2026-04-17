package com.umurinan.eda.ch07.commands;

import java.util.UUID;

/**
 * Sent to "inventory-commands" after payment succeeds.
 * The inventory service replies on "inventory-replies" with an {@code InventoryReply}.
 */
public record ReserveInventoryCommand(
        String sagaId,
        String orderId,
        String productId,
        int quantity,
        UUID idempotencyKey
) {}
