package com.shopassist.services.chat;

import com.shopassist.dto.ai.AssistantExchange;
import com.shopassist.dto.ai.AssistantReply;
import com.shopassist.dto.chat.ChatMessageResponse;
import com.shopassist.dto.chat.ChatRequest;
import com.shopassist.dto.chat.ChatResponse;
import com.shopassist.dto.chat.ConversationDetailResponse;
import com.shopassist.dto.chat.ConversationSummaryResponse;
import com.shopassist.entity.chat.ChatMessage;
import com.shopassist.services.ai.AssistantModel;
import com.shopassist.util.ai.SystemPrompts;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Runs a chat turn: record the question, ask the model, record the answer.
 *
 * <p>Deliberately <b>not</b> {@code @Transactional}. The database work lives in
 * {@link ConversationStore}, which commits the question before the model is
 * called and the answer after. Wrapping the whole turn in one transaction would
 * hold a pooled connection open for the several seconds a local model takes, and
 * — worse — an ordinary "no such order" from a tool would mark that shared
 * transaction rollback-only and fail the request at commit time, after the
 * assistant had already composed a good reply.
 *
 * <p>A consequence worth knowing: if the model fails, the shopper's question is
 * already committed and no assistant turn joins it. That is the right way round.
 * What someone asked is worth keeping; a reply that never existed is not.
 */
@Service
@Slf4j
public class ChatService {

    private final ConversationStore store;
    private final AssistantModel assistantModel;

    public ChatService(ConversationStore store, AssistantModel assistantModel) {
        this.store = store;
        this.assistantModel = assistantModel;
    }

    public ChatResponse send(ChatRequest request) {
        String question = request.message().strip();

        ConversationStore.PreparedTurn turn = store.startTurn(request.conversationId(), question);

        AssistantReply reply = assistantModel.reply(new AssistantExchange(
                SystemPrompts.WITH_TOOLS, turn.history(), question));

        ChatMessage answer = store.recordReply(turn.conversationRef(), reply);

        return new ChatResponse(turn.conversationRef(), ChatMessageResponse.from(answer));
    }

    public List<ConversationSummaryResponse> myConversations() {
        return store.myConversations();
    }

    public ConversationDetailResponse myConversation(String conversationId) {
        return store.myConversation(conversationId);
    }
}
