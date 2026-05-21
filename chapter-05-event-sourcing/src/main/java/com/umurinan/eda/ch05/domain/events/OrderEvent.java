package com.umurinan.eda.ch05.domain.events;

public sealed interface OrderEvent
        permits OrderPlacedEvent, OrderCancelledEvent {
}
