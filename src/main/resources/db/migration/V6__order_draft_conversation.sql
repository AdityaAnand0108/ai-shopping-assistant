-- Ties a proposed purchase to the conversation it was proposed in.
--
-- confirmOrder takes no arguments by design: the model cannot carry a reference
-- across turns without getting it wrong. It resolved "the shopper's most recent
-- draft", which is right within one conversation and wrong across two. A draft
-- left unconfirmed in one thread was the newest draft everywhere, so agreeing to
-- a purchase in a second thread could confirm the first one instead — a
-- different product, at a different price, in a conversation that never
-- mentioned it.
--
-- Nullable: drafts created from the checkout page belong to no conversation, and
-- are confirmed by reference rather than by recency.
ALTER TABLE order_drafts
    ADD COLUMN conversation_ref VARCHAR(36) NULL;

CREATE INDEX idx_order_drafts_conversation ON order_drafts (user_id, conversation_ref);
