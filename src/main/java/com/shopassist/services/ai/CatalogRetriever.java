package com.shopassist.services.ai;

import com.shopassist.dto.ai.SemanticMatch;
import java.util.List;

/**
 * Semantic recall over the product catalog.
 *
 * <p>An interface for the same reason {@code AssistantModel} is one: the search
 * path can then be tested without an embedding model running, and the store
 * behind it can change from a file to pgvector without touching a caller.
 *
 * <p>Returns SKUs and scores, never products. Retrieval decides <em>which</em>
 * items are plausibly relevant; the database remains the only source of what
 * they actually cost and whether they are in stock. Embeddings are a snapshot
 * and go stale — prices do not come from snapshots.
 */
public interface CatalogRetriever {

    List<SemanticMatch> findSimilar(String query, int topK);

    /** Whether the index is built and usable. */
    boolean isReady();
}
