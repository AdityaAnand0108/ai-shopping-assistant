package com.shopassist.common;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bounds on a chat turn.
 *
 * @param maxInputChars     longest message accepted from a shopper
 * @param maxTurnsInContext how many earlier turns are replayed to the model
 */
@ConfigurationProperties(prefix = "shopassist.guard")
public record ChatProperties(
        int maxInputChars,
        int maxTurnsInContext
) {
    public ChatProperties {
        if (maxInputChars <= 0) {
            maxInputChars = 1000;
        }
        if (maxTurnsInContext <= 0) {
            maxTurnsInContext = 12;
        }
    }
}
