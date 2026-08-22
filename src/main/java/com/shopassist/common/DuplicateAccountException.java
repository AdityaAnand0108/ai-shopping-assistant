package com.shopassist.common;

/**
 * A username or email is already registered.
 *
 * <p>Like {@link AccountLockedException} this admits that an account exists.
 * Signup cannot avoid it: a user who picks a taken username has to be told to
 * pick another one. Recorded in the limitations note.
 */
public class DuplicateAccountException extends RuntimeException {

    public DuplicateAccountException(String message) {
        super(message);
    }
}
