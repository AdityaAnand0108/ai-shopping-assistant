package com.shopassist.exception.ai;

/**
 * A shopper sent chat messages faster than the configured allowance.
 *
 * <p>Each turn costs seconds of model time, so an unthrottled client can
 * exhaust the machine for everyone else without doing anything obviously
 * abusive.
 */
public class RateLimitExceededException extends RuntimeException {

    private final long retryAfterSeconds;

    public RateLimitExceededException(long retryAfterSeconds) {
        super("Too many messages");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
