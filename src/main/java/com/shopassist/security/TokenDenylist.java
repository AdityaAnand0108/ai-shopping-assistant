package com.shopassist.security;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Revoked token ids, so that logout actually invalidates a token instead of
 * merely asking the client to forget it.
 *
 * <p>Entries are dropped once the token would have expired anyway, which bounds
 * the map at roughly the number of logouts within one token lifetime.
 *
 * <p><b>Known limitation:</b> this lives in memory, so revocations are lost on
 * restart and are not shared between instances. That is acceptable for a
 * single-node POC; a horizontally scaled deployment would move this to Redis
 * with a TTL equal to the token lifetime, which is the same interface with a
 * different backing store.
 */
@Component
@Slf4j
public class TokenDenylist {

    private final Map<String, Instant> revokedUntil = new ConcurrentHashMap<>();

    public void revoke(String tokenId, Instant expiresAt) {
        if (tokenId != null && expiresAt != null) {
            revokedUntil.put(tokenId, expiresAt);
        }
    }

    public boolean isRevoked(String tokenId) {
        Instant expiry = revokedUntil.get(tokenId);
        if (expiry == null) {
            return false;
        }
        if (expiry.isBefore(Instant.now())) {
            revokedUntil.remove(tokenId);
            return false;
        }
        return true;
    }

    /** Sweeps entries whose tokens have expired on their own. */
    @Scheduled(fixedDelay = 15 * 60 * 1000L)
    void purgeExpired() {
        Instant now = Instant.now();
        int before = revokedUntil.size();
        revokedUntil.entrySet().removeIf(entry -> entry.getValue().isBefore(now));
        int removed = before - revokedUntil.size();
        if (removed > 0) {
            log.debug("Purged {} expired token revocations", removed);
        }
    }

    int size() {
        return revokedUntil.size();
    }
}
