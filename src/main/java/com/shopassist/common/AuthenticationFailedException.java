package com.shopassist.common;

/**
 * Wrong username, wrong password, or a disabled account.
 *
 * <p>Deliberately one exception for all three. Callers receive an identical
 * response in every case, so the endpoint cannot be used to discover which
 * usernames exist.
 */
public class AuthenticationFailedException extends RuntimeException {

    public AuthenticationFailedException() {
        super("Invalid username or password");
    }
}
