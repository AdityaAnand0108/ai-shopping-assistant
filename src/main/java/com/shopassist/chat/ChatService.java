package com.shopassist.chat;

import com.shopassist.ai.client.AssistantExchange;
import com.shopassist.ai.client.AssistantModel;
import com.shopassist.ai.client.AssistantReply;
import com.shopassist.ai.prompt.SystemPrompts;
import com.shopassist.common.ChatProperties;
import com.shopassist.common.ResourceNotFoundException;
import com.shopassist.security.CurrentUserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Runs a chat turn: persist the question, ask the model, persist the answer.
 *
 * <p>Both turns are written whatever happens, so the record of what a shopper
 * asked survives even when the model fails to answer.
 */
@Service
@Slf4j
public class ChatService {

    private final ConversationRepository conversationRepository;
    private final ChatMessageRepository messageRepository;
    private final AssistantModel assistantModel;
    private final CurrentUserService currentUserService;
    private final ChatProperties properties;

    public ChatService(ConversationRepository conversationRepository,
                       ChatMessageRepository messageRepository,
                       AssistantModel assistantModel,
                       CurrentUserService currentUserService,
                       ChatProperties properties) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.assistantModel = assistantModel;
        this.currentUserService = currentUserService;
        this.properties = properties;
    }

    @Transactional
    public ChatResponse send(ChatRequest request) {
        String question = request.message().strip();
        Conversation conversation = resolveConversation(request.conversationId());
        conversation.titleFrom(question);

        // History is read before the new question is written, so the question is
        // not duplicated as both history and the current turn.
        List<AssistantExchange.HistoryTurn> history = recentHistory(conversation);

        messageRepository.save(attach(conversation, ChatMessage.fromShopper(question)));

        AssistantReply reply = assistantModel.reply(new AssistantExchange(
                SystemPrompts.CONVERSATIONAL, history, question));

        ChatMessage answer = messageRepository.save(attach(conversation,
                ChatMessage.fromAssistant(reply.content(), reply.model(), reply.latencyMs())));

        // Touches updated_at so the history list orders by recent activity.
        conversationRepository.save(conversation);

        return new ChatResponse(conversation.getPublicRef(), ChatMessageResponse.from(answer));
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

    private static ChatMessage attach(Conversation conversation, ChatMessage message) {
        message.setConversation(conversation);
        return message;
    }
}
