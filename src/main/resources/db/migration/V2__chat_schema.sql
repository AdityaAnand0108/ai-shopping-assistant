-- Phase 4: conversation history.
--
-- Every turn is persisted, including the assistant's own replies, the model that
-- produced them and how long they took. That record is what later phases build
-- the governance story on: Phase 8 attaches tool-call audit rows and shopper
-- feedback to these message ids, so any answer can be traced back to what
-- produced it.

CREATE TABLE conversations (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    public_ref VARCHAR(36)  NOT NULL,
    user_id    BIGINT       NOT NULL,
    title      VARCHAR(200),
    created_at TIMESTAMP    NOT NULL,
    updated_at TIMESTAMP    NOT NULL,
    CONSTRAINT uk_conversations_public_ref UNIQUE (public_ref),
    CONSTRAINT fk_conversations_user FOREIGN KEY (user_id) REFERENCES app_users (id)
);

CREATE INDEX idx_conversations_user ON conversations (user_id);

CREATE TABLE chat_messages (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    public_ref      VARCHAR(36) NOT NULL,
    conversation_id BIGINT      NOT NULL,
    role            VARCHAR(20) NOT NULL,
    content         TEXT        NOT NULL,
    model           VARCHAR(80),
    latency_ms      INT,
    created_at      TIMESTAMP   NOT NULL,
    CONSTRAINT uk_chat_messages_public_ref UNIQUE (public_ref),
    CONSTRAINT fk_chat_messages_conversation FOREIGN KEY (conversation_id)
        REFERENCES conversations (id)
);

CREATE INDEX idx_chat_messages_conversation ON chat_messages (conversation_id, created_at);
