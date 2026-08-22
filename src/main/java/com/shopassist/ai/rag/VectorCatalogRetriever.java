package com.shopassist.ai.rag;

import com.shopassist.catalog.Product;
import com.shopassist.catalog.ProductRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Embeds the catalog and answers similarity queries against it.
 *
 * <p>The index is persisted to a file and reused across restarts, because
 * embedding sixty products means sixty round trips to a local model — tolerable
 * once, tedious on every boot. It is rebuilt when the file is missing or when
 * the number of products no longer matches what was indexed, which catches the
 * common case of the catalog changing underneath a stale index.
 *
 * <p>Only the SKU is kept in each document's metadata. The embedded text is
 * there for similarity, not for reading back: everything shown to a shopper is
 * re-read from the database afterwards, so a stale embedding can never surface
 * an out-of-date price.
 */
@Component
@ConditionalOnProperty(prefix = "shopassist.rag", name = "enabled", havingValue = "true",
        matchIfMissing = true)
@Slf4j
public class VectorCatalogRetriever implements CatalogRetriever {

    private static final String SKU_METADATA = "sku";

    private final ProductRepository productRepository;
    private final RagProperties properties;
    private final SimpleVectorStore vectorStore;
    private final AtomicBoolean ready = new AtomicBoolean(false);

    public VectorCatalogRetriever(ProductRepository productRepository,
                                  RagProperties properties,
                                  EmbeddingModel embeddingModel) {
        this.productRepository = productRepository;
        this.properties = properties;
        this.vectorStore = SimpleVectorStore.builder(embeddingModel).build();
    }

    @Override
    public List<SemanticMatch> findSimilar(String query, int topK) {
        if (!ready.get() || query == null || query.isBlank()) {
            return List.of();
        }

        try {
            List<Document> hits = vectorStore.similaritySearch(SearchRequest.builder()
                    .query(query)
                    .topK(topK)
                    .similarityThreshold(properties.similarityThreshold())
                    .build());

            if (hits == null) {
                return List.of();
            }

            return hits.stream()
                    .map(doc -> new SemanticMatch(
                            String.valueOf(doc.getMetadata().get(SKU_METADATA)),
                            doc.getScore() == null ? 0.0 : doc.getScore()))
                    .filter(match -> match.sku() != null && !"null".equals(match.sku()))
                    .toList();

        } catch (Exception e) {
            // Retrieval is an enhancement, not a dependency. If the embedding
            // model is down, search must still work through SQL rather than the
            // whole catalog becoming unreachable.
            log.warn("Semantic search failed, falling back to keyword search: {}", e.getMessage());
            return List.of();
        }
    }

    @Override
    public boolean isReady() {
        return ready.get();
    }

    /**
     * Builds or loads the index. Called after the catalog is seeded.
     */
    @Transactional(readOnly = true)
    public void buildIndex() {
        long productCount = productRepository.count();
        if (productCount == 0) {
            log.info("Catalog is empty; nothing to index");
            return;
        }

        File store = new File(properties.storePath());
        if (loadFrom(store, productCount)) {
            return;
        }

        log.info("Embedding {} products — this takes a moment on first run", productCount);
        long startedAt = System.currentTimeMillis();

        List<Document> documents = productRepository.findAll().stream()
                .map(VectorCatalogRetriever::toDocument)
                .toList();

        try {
            vectorStore.add(documents);
            persist(store);
            ready.set(true);
            log.info("Indexed {} products in {}ms",
                    documents.size(), System.currentTimeMillis() - startedAt);
        } catch (Exception e) {
            log.error("Could not build the semantic index; search will use keywords only", e);
            ready.set(false);
        }
    }

    /** @return true if a usable index was loaded from disk */
    private boolean loadFrom(File store, long productCount) {
        if (!store.isFile()) {
            return false;
        }
        try {
            vectorStore.load(store);
            long indexed = countIndexed();
            if (indexed != productCount) {
                log.info("Stored index holds {} products but the catalog has {}; rebuilding",
                        indexed, productCount);
                return false;
            }
            ready.set(true);
            log.info("Loaded semantic index for {} products from {}", indexed, store);
            return true;
        } catch (Exception e) {
            log.warn("Could not read the stored index at {}; rebuilding. {}", store, e.getMessage());
            return false;
        }
    }

    /**
     * Counts what is actually in the store by querying it, since
     * SimpleVectorStore exposes no size. A threshold of zero returns everything.
     */
    private long countIndexed() {
        List<Document> all = vectorStore.similaritySearch(SearchRequest.builder()
                .query("product")
                .topK(Integer.MAX_VALUE)
                .similarityThresholdAll()
                .build());
        return all == null ? 0 : all.size();
    }

    private void persist(File store) {
        try {
            File parent = store.getParentFile();
            if (parent != null) {
                Files.createDirectories(parent.toPath());
            }
            vectorStore.save(store);
            log.info("Saved semantic index to {}", store);
        } catch (IOException | RuntimeException e) {
            // A store that cannot be saved still works in memory for this run.
            log.warn("Could not persist the semantic index to {}: {}", store, e.getMessage());
        }
    }

    private static Document toDocument(Product product) {
        return new Document(
                product.getSku(),
                product.toEmbeddableText(),
                Map.of(SKU_METADATA, product.getSku()));
    }
}
