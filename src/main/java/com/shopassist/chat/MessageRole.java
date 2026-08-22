package com.shopassist.chat;

public enum MessageRole {

    /** Something the shopper typed. */
    USER,

    /** Something the assistant produced. */
    ASSISTANT;

    public boolean isFromShopper() {
        return this == USER;
    }
}
