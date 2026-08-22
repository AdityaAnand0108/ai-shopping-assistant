package com.shopassist.order;

/**
 * The lifecycle of a proposed purchase.
 *
 * <p>Only {@link #PENDING} may be confirmed. {@link #CONFIRMED} is terminal and
 * re-confirming returns the existing order rather than creating a second, so a
 * model that repeats a tool call cannot charge twice.
 */
public enum DraftStatus {

    /** Proposed, awaiting the shopper's word. */
    PENDING,

    /** Turned into a real order. */
    CONFIRMED,

    /** The shopper said no. */
    CANCELLED
}
