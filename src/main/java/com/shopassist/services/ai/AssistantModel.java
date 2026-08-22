package com.shopassist.services.ai;

import com.shopassist.dto.ai.AssistantExchange;
import com.shopassist.dto.ai.AssistantReply;

/**
 * The application's own view of a language model.
 *
 * <p>A deliberate seam between the domain and Spring AI. It buys three things:
 * the chat service can be tested without a model server running, the provider
 * can change without touching anything that calls it, and Phase 5 has one place
 * to attach tool calling rather than threading it through the service layer.
 */
public interface AssistantModel {

    AssistantReply reply(AssistantExchange exchange);

    /** Whether the backing model server is currently reachable. */
    boolean isAvailable();
}
