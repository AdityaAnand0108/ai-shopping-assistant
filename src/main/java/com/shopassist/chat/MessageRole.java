package com.shopassist.chat;

/**
 * Who produced a turn.
 *
 * <p>There is no {@code SYSTEM} member: the system prompt is supplied fresh on
 * every call from {@code SystemPrompts} rather than stored as a message, so
 * changing it takes effect immediately instead of only for new threads.
 */
public enum MessageRole {

    /** Something the shopper typed. */
    USER,

    /** Something the assistant produced. */
    ASSISTANT;

    public boolean isFromShopper() {
        return this == USER;
    }
}
