package com.shopassist.services.chat;

import com.shopassist.dto.ai.AssistantExchange;
import com.shopassist.dto.ai.AssistantReply;
import com.shopassist.dto.chat.ChatMessageResponse;
import com.shopassist.dto.chat.ChatRequest;
import com.shopassist.dto.chat.ChatResponse;
import com.shopassist.dto.chat.ConversationDetailResponse;
import com.shopassist.dto.chat.ConversationSummaryResponse;
import com.shopassist.dto.chat.TurnAction;
import com.shopassist.dto.chat.TurnInsight;
import com.shopassist.dto.order.DraftResponse;
import com.shopassist.dto.order.OrderDetailResponse;
import com.shopassist.services.order.CheckoutService;
import com.shopassist.services.order.OrderService;
import com.shopassist.entity.chat.ChatMessage;
import com.shopassist.security.CurrentUserService;
import com.shopassist.services.ai.AssistantModel;
import com.shopassist.services.ai.guard.ChatRateLimiter;
import com.shopassist.services.ai.guard.GroundingCheck;
import com.shopassist.services.ai.guard.InputGuard;
import com.shopassist.services.ai.guard.OutputGuard;
import com.shopassist.services.ai.guard.ToolCallRecorder;
import com.shopassist.util.ai.SystemPrompts;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Runs a chat turn: check, record the question, ask the model, check again,
 * record the answer.
 *
 * <p>Deliberately <b>not</b> {@code @Transactional}. The database work lives in
 * {@link ConversationStore}, which commits the question before the model is
 * called and the answer after. Wrapping the whole turn in one transaction would
 * hold a pooled connection open for the several seconds a local model takes, and
 * — worse — an ordinary "no such order" from a tool would mark that shared
 * transaction rollback-only and fail the request at commit time, after the
 * assistant had already composed a good reply.
 *
 * <p>The guards sit around the model call in a deliberate order. Rate limiting
 * comes first because it is cheapest. Input inspection comes next, so a message
 * that is plainly an attack costs no inference time at all. Output inspection
 * and the grounding check run on the way back, against what the model actually
 * wrote.
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
    private final CurrentUserService currentUserService;
    private final ChatRateLimiter rateLimiter;
    private final InputGuard inputGuard;
    private final OutputGuard outputGuard;
    private final GroundingCheck groundingCheck;
    private final ToolCallRecorder toolCallRecorder;
    private final CheckoutService checkoutService;
    private final OrderService orderService;

    public ChatService(ConversationStore store,
                       AssistantModel assistantModel,
                       CurrentUserService currentUserService,
                       ChatRateLimiter rateLimiter,
                       InputGuard inputGuard,
                       OutputGuard outputGuard,
                       GroundingCheck groundingCheck,
                       ToolCallRecorder toolCallRecorder,
                       CheckoutService checkoutService,
                       OrderService orderService) {
        this.store = store;
        this.assistantModel = assistantModel;
        this.currentUserService = currentUserService;
        this.rateLimiter = rateLimiter;
        this.inputGuard = inputGuard;
        this.outputGuard = outputGuard;
        this.groundingCheck = groundingCheck;
        this.toolCallRecorder = toolCallRecorder;
        this.checkoutService = checkoutService;
        this.orderService = orderService;
    }

    public ChatResponse send(ChatRequest request) {
        String question = request.message().strip();
        rateLimiter.checkAndRecord(currentUserService.requireUserId());

        Optional<InputGuard.Refusal> refusal = inputGuard.inspect(question);
        if (refusal.isPresent()) {
            return refuseWithoutCallingModel(request, question, refusal.get());
        }

        ConversationStore.PreparedTurn turn = store.startTurn(request.conversationId(), question);

        AssistantReply reply;
        List<String> toolsUsed;
        java.util.Set<String> toolFacts;
        GroundingCheck.Result grounding;
        String draftReference;
        String placedOrderNumber;
        try {
            toolCallRecorder.startTurn(turn.conversationRef());
            reply = assistantModel.reply(new AssistantExchange(
                    SystemPrompts.WITH_TOOLS, turn.history(), question));

            toolsUsed = toolCallRecorder.toolsUsed();
            toolFacts = toolCallRecorder.identifiers();
            // Read before the finally block clears the recorder.
            draftReference = toolCallRecorder.draftReference();
            placedOrderNumber = toolCallRecorder.placedOrderNumber();
            grounding = groundingCheck.check(reply.content(),
                    toolCallRecorder.identifiers(),
                    toolCallRecorder.amounts(),
                    toolCallRecorder.anyToolRan());
        } finally {
            // The recorder is thread-local and request threads are pooled, so
            // failing to clear it would leak one shopper's tool results into
            // whatever request the thread handles next.
            toolCallRecorder.clear();
        }

        // Strip first, then inspect: the note is an artefact this application
        // injected, so removing it should not count as redacting the answer.
        String cleaned = outputGuard.stripInternalNotes(reply.content());
        String finalContent = outputGuard.inspect(cleaned).orElse(cleaned);
        boolean redacted = !finalContent.equals(cleaned);

        ChatMessage answer = store.recordReply(turn.conversationRef(),
                new AssistantReply(finalContent, reply.model(), reply.latencyMs()), toolFacts);

        return new ChatResponse(turn.conversationRef(), ChatMessageResponse.from(answer),
                new TurnInsight(toolsUsed, grounding.grounded(), grounding.unsupported(), redacted),
                actionFor(draftReference, placedOrderNumber));
    }

    /**
     * Resolves what the turn did into something a client can render.
     *
     * <p>Never throws. A purchase that succeeded must not be reported as a
     * failure because the read-back for the card did not work — the reply text
     * still stands on its own, and the shopper's orders page is authoritative
     * either way.
     *
     * <p>When a turn both prices and places a purchase, only the order is
     * returned: the draft has been spent, and offering a Confirm button for it
     * would invite a second click that buys nothing.
     */
    private TurnAction actionFor(String draftReference, String placedOrderNumber) {
        OrderDetailResponse order = null;
        if (placedOrderNumber != null) {
            try {
                order = orderService.myOrder(placedOrderNumber);
            } catch (RuntimeException e) {
                log.warn("Placed order {} could not be read back for the reply card: {}",
                        placedOrderNumber, e.getMessage());
            }
        }

        DraftResponse draft = null;
        if (order == null && draftReference != null) {
            try {
                draft = checkoutService.view(draftReference);
            } catch (RuntimeException e) {
                log.warn("Draft {} could not be read back for the reply card: {}",
                        draftReference, e.getMessage());
            }
        }

        return TurnAction.of(draft, order);
    }

    /**
     * Answers a refused message without spending a model call, while still
     * recording both turns so the thread reads normally and the attempt is not
     * silently lost.
     */
    private ChatResponse refuseWithoutCallingModel(ChatRequest request, String question,
                                                   InputGuard.Refusal refusal) {

        ConversationStore.PreparedTurn turn = store.startTurn(request.conversationId(), question);
        ChatMessage answer = store.recordReply(turn.conversationRef(),
                new AssistantReply(refusal.reply(), "guardrail:" + refusal.category(), 0L),
                java.util.Set.of());

        return new ChatResponse(turn.conversationRef(), ChatMessageResponse.from(answer),
                TurnInsight.refused(), null);
    }

    public List<ConversationSummaryResponse> myConversations() {
        return store.myConversations();
    }

    public ConversationDetailResponse myConversation(String conversationId) {
        return store.myConversation(conversationId);
    }
}
