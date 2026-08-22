package com.shopassist.chat;

import com.shopassist.ai.client.AssistantExchange;
import com.shopassist.ai.client.AssistantReply;
import com.shopassist.common.ChatProperties;
import com.shopassist.common.ResourceNotFoundException;
import com.shopassist.security.CurrentUserService;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The transactional half of a chat turn.
 *
 * <p>Split out from {@link ChatService} deliberately, for two reasons.
 *
 * <p>The first is correctness. A tool that finds nothing throws — an order that
 * is not yours is a not-found — and when that exception crosses an inner
 * {@code @Transactional} boundary, Spring marks the surrounding transaction
 * rollback-only. If the model call sat inside one transaction with the writes,
 * an ordinary "no such order" answer would poison the commit and the whole
 * request would fail at the end with an UnexpectedRollbackException, long after
 * the assistant had composed a perfectly good reply.
 *
 * <p>The second is resource use. A local model takes seconds per reply, and a
 * reply that calls tools takes longer still. Holding a pooled database
 * connection open across that wait would let a handful of concurrent shoppers
 * exhaust the pool while doing no database work at all.
 *
 * <p>So the writes happen in two short transactions, and the model call happens
 * between them, in none.
 */
@Service
public class ConversationStore {

    private final ConversationRepository conversationRepository;
    private final ChatMessageRepository messageRepository;
    private final CurrentUserService currentUserService;
    private final ChatProperties properties;

    public ConversationStore(ConversationRepository conversationRepository,
                             ChatMessageRepository messageRepository,
                             CurrentUserService currentUserService,
                             ChatProperties properties) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.currentUserService = currentUserService;
        this.properties = properties;
    }

    /**
     * Opens or continues a thread and records the shopper's question.
     *
     * <p>Committed before the model is called, so what a shopper asked survives
     * even if the assistant then fails to answer.
     */
    @Transactional
    public PreparedTurn startTurn(String conversationId, String question) {
        Conversation conversation = resolveConversation(conversationId);
        conversation.titleFrom(question);

        // History is read before the question is written, so the current turn is
        // not duplicated as both history and user message.
        List<AssistantExchange.HistoryTurn> history = recentHistory(conversation);

        ChatMessage asked = ChatMessage.fromShopper(question);
        asked.setConversation(conversation);
        messageRepository.save(asked);
        conversationRepository.save(conversation);

        return new PreparedTurn(conversation.getPublicRef(), history);
    }

    /** Records the assistant's answer against the thread. */
    @Transactional
    public ChatMessage recordReply(String conversationRef, AssistantReply reply) {
        Conversation conversation = requireOwnedConversation(conversationRef);

        ChatMessage answer = ChatMessage.fromAssistant(
                reply.content(), reply.model(), reply.latencyMs());
        answer.setConversation(conversation);
        ChatMessage saved = messageRepository.save(answer);

        // Touches updated_at so the history list orders by recent activity.
        conversationRepository.save(conversation);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<ConversationSummaryResponse> myConversations() {
        return conversationRepository
                .findByUserIdOrderByUpdatedAtDesc(currentUserService.requireUserId())
                .stream()
                .map(conversation -> ConversationSummaryResponse.from(
                        conversation, messageRepository.countByConversationId(conversation.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public ConversationDetailResponse myConversation(String conversationId) {
        Conversation conversation = requireOwnedConversation(conversationId);
        return ConversationDetailResponse.from(conversation,
                messageRepository.findByConversationIdOrderByCreatedAtAsc(conversation.getId()));
    }

    /**
     * Continues the named thread, or opens a new one.
     *
     * <p>A conversation reference that is unknown, or belongs to someone else,
     * raises the same not-found either way — the same indistinguishability the
     * order lookups rely on.
     */
    private Conversation resolveConversation(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return conversationRepository.save(Conversation.builder()
                    .user(currentUserService.requireUser())
                    .build());
        }
        return requireOwnedConversation(conversationId);
    }

    private Conversation requireOwnedConversation(String conversationId) {
        return conversationRepository
                .findByPublicRefAndUserId(conversationId, currentUserService.requireUserId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No conversation found with id " + conversationId));
    }

    /**
     * The last few turns, oldest first.
     *
     * <p>Fetched newest-first with a limit and then reversed, so a long thread
     * costs one bounded query rather than loading every message ever sent just to
     * discard most of them.
     */
    private List<AssistantExchange.HistoryTurn> recentHistory(Conversation conversation) {
        if (conversation.getId() == null) {
            return List.of();
        }

        List<ChatMessage> newestFirst = messageRepository.findByConversationIdOrderByCreatedAtDesc(
                conversation.getId(), Limit.of(properties.maxTurnsInContext()));

        List<ChatMessage> oldestFirst = new ArrayList<>(newestFirst);
        oldestFirst.sort(Comparator.comparing(ChatMessage::getCreatedAt)
                .thenComparing(ChatMessage::getId));

        return oldestFirst.stream()
                .map(message -> new AssistantExchange.HistoryTurn(
                        message.getRole().isFromShopper(), message.getContent()))
                .toList();
    }

    /**
     * @param conversationRef the thread the turn belongs to
     * @param history         earlier turns to replay, oldest first
     */
    public record PreparedTurn(String conversationRef,
                               List<AssistantExchange.HistoryTurn> history) {
    }
}
