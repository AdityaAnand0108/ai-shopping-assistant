package com.shopassist.ai.client;

import com.shopassist.ai.tools.CatalogTools;
import com.shopassist.ai.tools.OrderTools;
import com.shopassist.ai.tools.PurchaseTools;
import com.shopassist.common.ModelUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The Ollama-backed implementation of {@link AssistantModel}.
 *
 * <p>This is the only class in the project that knows Spring AI exists, and the
 * single place where the assistant's tools are attached.
 */
@Component
@Slf4j
public class SpringAiAssistantModel implements AssistantModel {

    private final ChatClient chatClient;
    private final ModelProperties properties;
    private final Object[] tools;

    public SpringAiAssistantModel(ChatClient chatClient,
                                  ModelProperties properties,
                                  CatalogTools catalogTools,
                                  OrderTools orderTools,
                                  PurchaseTools purchaseTools) {
        this.chatClient = chatClient;
        this.properties = properties;
        // Registered here rather than passed through AssistantExchange: which
        // tools exist is a property of this adapter, not something a caller in
        // the domain should be able to vary per request.
        this.tools = new Object[]{catalogTools, orderTools, purchaseTools};
    }

    @Override
    public AssistantReply reply(AssistantExchange exchange) {
        long startedAt = System.currentTimeMillis();
        try {
            String content = chatClient.prompt()
                    .system(exchange.systemPrompt())
                    .messages(toSpringAiMessages(exchange.history()))
                    .user(exchange.userMessage())
                    .tools(tools)
                    .options(OllamaChatOptions.builder()
                            .model(properties.chatModel())
                            .temperature(properties.temperature())
                            .build())
                    .call()
                    .content();

            long latency = System.currentTimeMillis() - startedAt;

            if (content == null || content.isBlank()) {
                // An empty completion is a failure, not an answer. Returning it
                // would surface as a blank chat bubble with no explanation.
                throw new ModelUnavailableException("The assistant returned an empty response");
            }

            log.debug("Model replied in {}ms", latency);
            return new AssistantReply(content.strip(), properties.chatModel(), latency);

        } catch (ModelUnavailableException e) {
            throw e;
        } catch (Exception e) {
            // Connection refused, model not pulled, timeout, malformed response:
            // all of it is "the assistant is not answering right now" from a
            // shopper's point of view, and none of the detail belongs in a
            // response body.
            log.error("Model call failed after {}ms", System.currentTimeMillis() - startedAt, e);
            throw new ModelUnavailableException("The assistant is temporarily unavailable");
        }
    }

    @Override
    public boolean isAvailable() {
        try {
            chatClient.prompt()
                    .user("ping")
                    .options(OllamaChatOptions.builder()
                            .model(properties.chatModel())
                            .numPredict(1)
                            .build())
                    .call()
                    .content();
            return true;
        } catch (Exception e) {
            log.debug("Availability probe failed: {}", e.getMessage());
            return false;
        }
    }

    private static List<Message> toSpringAiMessages(List<AssistantExchange.HistoryTurn> history) {
        List<Message> messages = new ArrayList<>(history.size());
        for (AssistantExchange.HistoryTurn turn : history) {
            messages.add(turn.fromShopper()
                    ? new UserMessage(turn.content())
                    : new AssistantMessage(turn.content()));
        }
        return messages;
    }
}
