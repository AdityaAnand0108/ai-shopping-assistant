package com.shopassist.chat;

import com.shopassist.ai.client.AssistantExchange;
import com.shopassist.ai.client.AssistantModel;
import com.shopassist.ai.client.AssistantReply;
import com.shopassist.common.ModelUnavailableException;

import java.util.ArrayList;
import java.util.List;

/**
 * A model that records what it was asked and answers from a script.
 *
 * <p>The point of {@link AssistantModel} being an interface: the whole chat
 * surface can be tested without a model server, and the tests can assert on
 * exactly what would have been sent to a real one — which prompt, which history,
 * in which order. Those are the things that quietly break, and a live model
 * would hide them behind non-deterministic prose.
 */
public class StubAssistantModel implements AssistantModel {

    private final List<AssistantExchange> received = new ArrayList<>();
    private String nextReply = "Sure, I can help with that.";
    private boolean available = true;
    private RuntimeException failWith;

    @Override
    public AssistantReply reply(AssistantExchange exchange) {
        received.add(exchange);
        if (failWith != null) {
            throw failWith;
        }
        return new AssistantReply(nextReply, "stub-model", 42L);
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    public void willReply(String reply) {
        this.nextReply = reply;
        this.failWith = null;
    }

    public void willFailAsUnavailable() {
        this.failWith = new ModelUnavailableException("The assistant is temporarily unavailable");
    }

    public void reset() {
        received.clear();
        nextReply = "Sure, I can help with that.";
        failWith = null;
        available = true;
    }

    public AssistantExchange lastExchange() {
        if (received.isEmpty()) {
            throw new AssertionError("The model was never called");
        }
        return received.get(received.size() - 1);
    }

    public int callCount() {
        return received.size();
    }
}
