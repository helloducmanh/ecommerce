package com.shopnow.domain.model;

/**
 * Thrown when an order cannot transition to the requested state
 * (e.g. cancelling a non-pending order, or a losing concurrent cancel).
 * Mapped to 409 CONFLICT by {@code GlobalExceptionHandler}.
 */
public class OrderStateException extends RuntimeException {

    public OrderStateException(String message) {
        super(message);
    }
}
