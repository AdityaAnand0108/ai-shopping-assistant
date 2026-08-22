package com.shopassist.enums.order;

/**
 * Lifecycle of an order. The sequence below is the normal happy path; an order
 * may leave it early for CANCELLED, or leave it after delivery for RETURNED.
 */
public enum OrderStatus {
    PLACED,
    CONFIRMED,
    PACKED,
    SHIPPED,
    OUT_FOR_DELIVERY,
    DELIVERED,
    CANCELLED,
    RETURNED;

    public boolean isTerminal() {
        return this == DELIVERED || this == CANCELLED || this == RETURNED;
    }

    public boolean isCancellable() {
        return this == PLACED || this == CONFIRMED || this == PACKED;
    }
}
