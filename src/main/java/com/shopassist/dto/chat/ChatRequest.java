package com.shopassist.dto.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A shopper's message.
 *
 * @param message        what they typed
 * @param conversationId the thread to continue; null starts a new one
 */
public record ChatRequest(

        @NotBlank(message = "Message is required")
        @Size(max = 1000, message = "Message must be at most 1000 characters")
        String message,

        @Size(max = 36)
        String conversationId
) {
}
