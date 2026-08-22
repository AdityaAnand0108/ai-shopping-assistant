package com.shopassist.ai.client;

/**
 * Which model to talk to and how deterministically.
 *
 * @param chatModel   Ollama model tag used for chat
 * @param temperature 0 is as close to reproducible as a local model gets; kept
 *                    low because this assistant reports facts fetched from a
 *                    database rather than writing prose, and a creative
 *                    rephrasing of an order status is simply a wrong answer
 */
@org.springframework.boot.context.properties.ConfigurationProperties(prefix = "shopassist.model")
public record ModelProperties(
        String chatModel,
        String embeddingModel,
        Double temperature
) {
    public ModelProperties {
        if (chatModel == null || chatModel.isBlank()) {
            chatModel = "qwen2.5:7b";
        }
        if (embeddingModel == null || embeddingModel.isBlank()) {
            embeddingModel = "nomic-embed-text";
        }
        if (temperature == null) {
            temperature = 0.1;
        }
    }
}
