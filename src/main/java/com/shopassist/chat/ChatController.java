package com.shopassist.chat;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The chat surface. Every path requires a token: a conversation belongs to a
 * shopper, and identity is what scopes what the assistant may look at.
 */
@RestController
@RequestMapping("/api/chat")
@Tag(name = "Chat", description = "Conversational shopping assistant")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping
    @Operation(summary = "Send a message and get the assistant's reply")
    public ChatResponse send(@Valid @RequestBody ChatRequest request) {
        return chatService.send(request);
    }

    @GetMapping("/conversations")
    @Operation(summary = "List your conversations, most recently active first")
    public List<ConversationSummaryResponse> myConversations() {
        return chatService.myConversations();
    }

    @GetMapping("/conversations/{conversationId}")
    @Operation(summary = "Replay one of your conversations")
    public ConversationDetailResponse myConversation(@PathVariable String conversationId) {
        return chatService.myConversation(conversationId);
    }
}
