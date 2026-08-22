/**
 * Conversations with the assistant.
 *
 * <p>Threads are owner-scoped exactly as orders are, and every turn is
 * persisted with the model that produced it and how long it took.
 *
 * <p>Note the split between {@link com.shopassist.chat.ChatService} and
 * {@link com.shopassist.chat.ConversationStore}: the database work happens in
 * two short transactions with the model call in neither. Holding one
 * transaction across a multi-second model call would pin a pooled connection,
 * and an ordinary not-found from a tool would mark it rollback-only and fail
 * the request at commit.
 */
package com.shopassist.chat;
