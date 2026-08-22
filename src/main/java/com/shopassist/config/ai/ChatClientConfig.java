package com.shopassist.config.ai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Builds the single {@link ChatClient} the application uses.
 *
 * <p>No default system prompt is set here. The system prompt is supplied per
 * call by {@code SystemPrompts}, so it stays visible next to the code that
 * decides what the assistant is allowed to do rather than hidden in wiring.
 */
@Configuration
public class ChatClientConfig {

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder.build();
    }
}
