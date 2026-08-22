package com.shopassist.common;

/**
 * The language model could not be reached or produced nothing usable.
 *
 * <p>Distinct from a generic failure so the caller receives a 503 with a clear
 * "try again" rather than a 500 that looks like a bug in the shop.
 */
public class ModelUnavailableException extends RuntimeException {

    public ModelUnavailableException(String message) {
        super(message);
    }
}
