package com.shopassist.catalog;

import com.shopassist.ai.rag.CatalogRetriever;
import com.shopassist.ai.rag.SemanticMatch;
import com.shopassist.common.PageResponse;
import com.shopassist.common.ResourceNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Catalog reads.
 *
 * <p>Returns DTOs, never entities. The assistant's product tools call straight
 * into this class, so whatever a tool can see is exactly what an anonymous HTTP
 * client can see — there is no privileged path that returns more.
 */
@Service
@Transactional(readOnly = true)
@Slf4j
public class ProductService {

    /** Retrieval must clear this bar before it is preferred over keyword search. */
    private static final int MIN_USEFUL_CANDIDATES = 1;

    private final ProductRepository productRepository;
    private final Optional<CatalogRetriever> retriever;
    private final int retrievalTopK;

    public ProductService(ProductRepository productRepository,
                          Optional<CatalogRetriever> retriever,
                          com.shopassist.ai.rag.RagProperties ragProperties) {
        this.productRepository = productRepository;
        this.retriever = retriever;
        this.retrievalTopK = ragProperties.topK();
    }

    /**
     * Searches the catalog, semantically when possible and by keyword otherwise.
     *
     * <p>The two are not alternatives so much as halves of one query. Retrieval
     * decides which products a phrase like "something to keep me warm" is
     * <em>about</em> — a job SQL {@code LIKE} cannot do, because no hoodie
     * description contains the word "warm" as a searchable token in the way a
     * shopper means it. The filters that follow decide what may actually be
     * shown, and those stay in SQL because price and stock are facts rather than
     * similarities.
     *
     * <p>Retrieval never has the last word. If it returns nothing useful, or the
     * embedding model is unreachable, the keyword path runs instead and the
     * catalog stays searchable.
     */
    public PageResponse<ProductSummaryResponse> search(ProductSearchCriteria criteria) {
        if (criteria.text() != null && retriever.map(CatalogRetriever::isReady).orElse(false)) {
            Optional<PageResponse<ProductSummaryResponse>> semantic = semanticSearch(criteria);
            if (semantic.isPresent()) {
                return semantic.get();
            }
        }
        return keywordSearch(criteria);
    }

    private Optional<PageResponse<ProductSummaryResponse>> semanticSearch(
            ProductSearchCriteria criteria) {

        List<SemanticMatch> matches =
                retriever.orElseThrow().findSimilar(criteria.text(), retrievalTopK);
        if (matches.size() < MIN_USEFUL_CANDIDATES) {
            return Optional.empty();
        }

        // Rank by position, so the ordering the embedding model produced survives
        // the round trip through SQL.
        Map<String, Integer> rankBySku = new LinkedHashMap<>();
        for (SemanticMatch match : matches) {
            rankBySku.putIfAbsent(match.sku().toUpperCase(), rankBySku.size());
        }

        List<Product> allowed = productRepository.findBySkusWithFilters(
                rankBySku.keySet(),
                criteria.brand(),
                criteria.category(),
                criteria.minPrice(),
                criteria.maxPrice(),
                criteria.inStockOnly());

        if (allowed.isEmpty()) {
            // Retrieval found things the filters then excluded — for instance a
            // warm jacket above the shopper's price cap. Falling back to keyword
            // search gives a second chance rather than an empty page.
            return Optional.empty();
        }

        List<Product> ranked = allowed.stream()
                .sorted(Comparator.comparingInt(p -> rankBySku.getOrDefault(
                        p.getSku().toUpperCase(), Integer.MAX_VALUE)))
                .toList();

        log.debug("Semantic search for '{}' returned {} candidates, {} survived filtering",
                criteria.text(), matches.size(), ranked.size());

        return Optional.of(paginate(ranked, criteria));
    }

    private PageResponse<ProductSummaryResponse> keywordSearch(ProductSearchCriteria criteria) {
        Page<Product> results = productRepository.search(
                criteria.text(),
                criteria.brand(),
                criteria.category(),
                criteria.minPrice(),
                criteria.maxPrice(),
                criteria.inStockOnly(),
                criteria.toPageable());

        return PageResponse.from(results, ProductSummaryResponse::from);
    }

    /**
     * Pages an already-ranked list in memory.
     *
     * <p>Acceptable because the list is bounded by the retrieval limit, not by
     * the catalog size — a larger catalog changes how many candidates come back,
     * not how much is held here.
     */
    private static PageResponse<ProductSummaryResponse> paginate(
            List<Product> ranked, ProductSearchCriteria criteria) {

        int total = ranked.size();
        int from = Math.min(criteria.page() * criteria.size(), total);
        int to = Math.min(from + criteria.size(), total);
        int totalPages = (int) Math.ceil((double) total / criteria.size());

        List<ProductSummaryResponse> content = ranked.subList(from, to).stream()
                .map(ProductSummaryResponse::from)
                .toList();

        return new PageResponse<>(content, criteria.page(), criteria.size(), total,
                totalPages, criteria.page() == 0, to >= total);
    }

    public ProductDetailResponse findBySku(String sku) {
        return productRepository.findBySkuIgnoreCase(sku)
                .map(ProductDetailResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("No product with SKU " + sku));
    }

    public CatalogFiltersResponse filters() {
        return new CatalogFiltersResponse(
                productRepository.findDistinctBrands(),
                productRepository.findDistinctCategories());
    }

    /**
     * Whether the requested quantity can be bought right now.
     *
     * <p>Answers yes or no rather than returning the stock level, so the caller
     * learns only what it needs to complete a purchase.
     */
    public boolean canFulfil(String sku, int quantity) {
        if (quantity <= 0) {
            return false;
        }
        return productRepository.findBySkuIgnoreCase(sku)
                .map(product -> product.getStockQuantity() >= quantity)
                .orElse(false);
    }

    /** Entity access for callers inside the domain, such as order creation. */
    public Product requireEntity(String sku) {
        return productRepository.findBySkuIgnoreCase(sku)
                .orElseThrow(() -> new ResourceNotFoundException("No product with SKU " + sku));
    }

    public List<Product> requireEntities(List<String> skus) {
        return productRepository.findBySkuInIgnoreCase(skus);
    }
}
