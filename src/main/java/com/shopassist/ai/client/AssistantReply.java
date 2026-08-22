package com.shopassist.ai.client;

/**
 * What the model produced, with the provenance needed to record it.
 *
 * @param content   the reply text
 * @param model     which model generated it
 * @param latencyMs how long the call took
 */
public record AssistantReply(
        String content,
        String model,
        long latencyMs
) {
}
