package com.shopassist.exception.ai;

/**
 * A message was refused before it reached the model.
 *
 * <p>Carries a shopper-facing message on purpose. Telling someone which pattern
 * tripped would be a tuning guide for getting past it, so the detail stays in
 * the logs and the caller sees only that the request was declined.
 */
public class GuardrailViolationException extends RuntimeException {

    public GuardrailViolationException(String message) {
        super(message);
    }
}
