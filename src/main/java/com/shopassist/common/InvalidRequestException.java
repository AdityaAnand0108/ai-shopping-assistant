package com.shopassist.common;

/**
 * The request parsed correctly but asks for something incoherent, such as a
 * minimum price above the maximum.
 *
 * <p>Distinct from {@link IllegalArgumentException} so that a genuine internal
 * bug is never mistaken for user error and reported as a 400.
 */
public class InvalidRequestException extends RuntimeException {

    public InvalidRequestException(String message) {
        super(message);
    }
}
