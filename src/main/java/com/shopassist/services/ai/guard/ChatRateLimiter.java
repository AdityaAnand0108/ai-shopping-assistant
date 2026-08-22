package com.shopassist.services.ai.guard;

import com.shopassist.config.ai.GuardProperties;
import com.shopassist.exception.ai.RateLimitExceededException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Caps how many chat turns a shopper may take per minute.
 *
 * <p>Chat is unlike the rest of the API: a single turn occupies the machine for
 * seconds of model time, and one impatient client can starve everyone else
 * without doing anything that looks like abuse. The catalog endpoints need no
 * equivalent because they return in milliseconds.
 *
 * <p>A sliding window rather than a fixed one, so twenty messages cannot be sent
 * at 11:59:59 and twenty more at 12:00:00.
 *
 * <p><b>Known limitation:</b> counters live in memory, so the limit is per
 * instance and resets on restart — the same trade as {@code TokenDenylist}, and
 * the same fix in a scaled deployment: move the counter to Redis.
 */
@Component
@Slf4j
public class ChatRateLimiter {

    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final GuardProperties properties;
    private final Map<Long, Deque<Instant>> turnsByUser = new ConcurrentHashMap<>();

    public ChatRateLimiter(GuardProperties properties) {
        this.properties = properties;
    }

    /**
     * Records a turn for this shopper.
     *
     * @throws RateLimitExceededException if they are over the allowance, with
     *                                    the seconds until the oldest turn ages
     *                                    out of the window
     */
    public void checkAndRecord(Long userId) {
        Instant now = Instant.now();
        Instant cutoff = now.minus(WINDOW);

        // compute() so the read, prune and append happen atomically per user.
        turnsByUser.compute(userId, (id, turns) -> {
            Deque<Instant> window = turns == null ? new ArrayDeque<>() : turns;
            while (!window.isEmpty() && window.peekFirst().isBefore(cutoff)) {
                window.pollFirst();
            }
            if (window.size() >= properties.messagesPerMinute()) {
                long retryAfter = Math.max(1,
                        Duration.between(cutoff, window.peekFirst()).getSeconds());
                log.info("Rate limited user {} after {} messages in the last minute",
                        id, window.size());
                throw new RateLimitExceededException(retryAfter);
            }
            window.addLast(now);
            return window;
        });
    }

    /** Drops windows for shoppers who have stopped talking, so the map stays bounded. */
    @Scheduled(fixedDelay = 10 * 60 * 1000L)
    void purgeIdle() {
        Instant cutoff = Instant.now().minus(WINDOW);
        turnsByUser.entrySet().removeIf(e -> {
            Deque<Instant> window = e.getValue();
            return window.isEmpty() || window.peekLast().isBefore(cutoff);
        });
    }

    /** Test seam: forgets everything, so one test's traffic cannot fail another. */
    public void reset() {
        turnsByUser.clear();
    }
}
