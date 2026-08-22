package com.shopassist.order;

public enum DraftStatus {

    /** Proposed, awaiting the shopper's word. */
    PENDING,

    /** Turned into a real order. */
    CONFIRMED,

    /** The shopper said no. */
    CANCELLED
}
