package com.shopassist.services.ai;

import com.shopassist.dto.ai.SemanticMatch;
import java.util.ArrayList;
import java.util.List;

/**
 * A retriever that returns a scripted ranking.
 *
 * <p>Lets the hybrid search path be tested for the behaviour that actually
 * matters — that retrieval proposes and SQL disposes, that ranking survives the
 * round trip, and that an unavailable index falls back to keywords — without a
 * live embedding model, whose output would be neither fast nor deterministic.
 */
public class StubCatalogRetriever implements CatalogRetriever {

    private final List<String> queries = new ArrayList<>();
    private List<SemanticMatch> nextMatches = List.of();
    private boolean ready = true;
    private RuntimeException failWith;

    @Override
    public List<SemanticMatch> findSimilar(String query, int topK) {
        queries.add(query);
        if (failWith != null) {
            throw failWith;
        }
        return nextMatches.stream().limit(topK).toList();
    }

    @Override
    public boolean isReady() {
        return ready;
    }

    /** Scripts the ranking, best match first. */
    public void willReturn(String... skus) {
        List<SemanticMatch> matches = new ArrayList<>();
        double score = 0.95;
        for (String sku : skus) {
            matches.add(new SemanticMatch(sku, score));
            score -= 0.05;
        }
        this.nextMatches = List.copyOf(matches);
        this.failWith = null;
    }

    public void willReturnNothing() {
        this.nextMatches = List.of();
        this.failWith = null;
    }

    public void isUnavailable() {
        this.ready = false;
    }

    public void reset() {
        queries.clear();
        nextMatches = List.of();
        ready = true;
        failWith = null;
    }

    public List<String> queriesSeen() {
        return List.copyOf(queries);
    }
}
