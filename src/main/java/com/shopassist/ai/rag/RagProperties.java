package com.shopassist.ai.rag;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Retrieval settings.
 *
 * @param enabled             whether to build and use the semantic index
 * @param storePath           where the embedded catalog is persisted
 * @param topK                candidates pulled from the index before filtering
 * @param similarityThreshold below this, a match is treated as noise
 */
@ConfigurationProperties(prefix = "shopassist.rag")
public record RagProperties(
        boolean enabled,
        String storePath,
        int topK,
        double similarityThreshold
) {
    public RagProperties {
        if (storePath == null || storePath.isBlank()) {
            storePath = "./data/vector-store.json";
        }
        if (topK <= 0) {
            // Deliberately larger than the number of results ever shown. Hard
            // filters run after retrieval, so a query for "warm jacket under
            // ₹3000" needs enough candidates that the price filter still leaves
            // something behind.
            topK = 40;
        }
        if (similarityThreshold <= 0) {
            similarityThreshold = 0.55;
        }
    }
}
