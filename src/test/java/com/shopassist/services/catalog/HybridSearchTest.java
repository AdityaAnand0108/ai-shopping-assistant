package com.shopassist.services.catalog;

import com.shopassist.dto.PageResponse;
import com.shopassist.dto.catalog.ProductSearchCriteria;
import com.shopassist.dto.catalog.ProductSummaryResponse;
import com.shopassist.enums.catalog.ProductSort;
import com.shopassist.services.DemoDataInstaller;
import com.shopassist.services.ai.CatalogRetriever;
import com.shopassist.services.ai.StubCatalogRetriever;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The hybrid search path: retrieval proposes, SQL disposes.
 *
 * <p>Run against a scripted retriever rather than a live embedding model. What
 * is being tested is the contract between the two halves — that hard filters
 * still bind, that ranking survives, and that a failure degrades to keyword
 * search — none of which should depend on how a particular model happens to
 * embed a particular phrase.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class HybridSearchTest {

    @TestConfiguration
    static class StubRetrieverConfig {
        @Bean
        @Primary
        CatalogRetriever stubCatalogRetriever() {
            return new StubCatalogRetriever();
        }
    }

    @Autowired
    private ProductService productService;

    @Autowired
    private CatalogRetriever retriever;

    @Autowired
    private DemoDataInstaller installer;

    private StubCatalogRetriever stub;

    @BeforeEach
    void setUp() {
        installer.install();
        stub = (StubCatalogRetriever) retriever;
        stub.reset();
    }

    // --- what retrieval adds ------------------------------------------------

    @Test
    void findsProductsAKeywordSearchWouldMiss() {
        // No product description contains "keep me warm", so the keyword path
        // finds nothing. Retrieval is what makes the phrase answerable.
        assertThat(keyword("something to keep me warm").content()).isEmpty();

        stub.willReturn("NIK-HD-001", "UNQ-JK-001", "ADI-HD-001");
        var results = search("something to keep me warm", null, null, null, null, false);

        assertThat(results.content())
                .extracting(ProductSummaryResponse::sku)
                .containsExactly("NIK-HD-001", "UNQ-JK-001", "ADI-HD-001");
    }

    @Test
    void preservesTheRankingRetrievalProduced() {
        stub.willReturn("UNQ-JK-001", "NIK-HD-001", "ZAR-JK-001");
        var results = search("warm layer", null, null, null, null, false);

        // Ranking must survive the round trip through SQL, which returns rows in
        // whatever order it likes.
        assertThat(results.content())
                .extracting(ProductSummaryResponse::sku)
                .containsExactly("UNQ-JK-001", "NIK-HD-001", "ZAR-JK-001");
    }

    @Test
    void passesTheShoppersWordsToRetrieval() {
        stub.willReturn("NIK-HD-001");
        search("cosy winter jacket", null, null, null, null, false);

        assertThat(stub.queriesSeen()).containsExactly("cosy winter jacket");
    }

    // --- what SQL still controls --------------------------------------------

    @Test
    void aPriceCapStillBindsOnSemanticResults() {
        // Retrieval has no idea what anything costs, so the filter has to.
        stub.willReturn("UNQ-JK-001", "ZAR-JK-001", "NIK-HD-001", "ADI-HD-001");
        var results = search("warm layer", null, null, null, BigDecimal.valueOf(65), false);

        assertThat(results.content())
                .extracting(ProductSummaryResponse::sku)
                .containsExactly("NIK-HD-001", "ADI-HD-001");
        assertThat(results.content())
                .allSatisfy(p -> assertThat(p.price())
                        .isLessThanOrEqualTo(BigDecimal.valueOf(65)));
    }

    @Test
    void aBrandFilterStillBindsOnSemanticResults() {
        stub.willReturn("NIK-HD-001", "ADI-HD-001", "UNQ-JK-001");
        var results = search("warm layer", "Adidas", null, null, null, false);

        assertThat(results.content())
                .extracting(ProductSummaryResponse::sku)
                .containsExactly("ADI-HD-001");
    }

    @Test
    void anOutOfStockItemIsStillExcludedWhenOnlyAvailableWanted() {
        stub.willReturn("NIK-TS-004", "NIK-TS-001");
        var results = search("training top", null, null, null, null, true);

        assertThat(results.content())
                .extracting(ProductSummaryResponse::sku)
                .containsExactly("NIK-TS-001")
                .doesNotContain("NIK-TS-004");
    }

    @Test
    void aRetrievedSkuThatIsNoLongerInTheCatalogIsDropped() {
        // An index can go stale. A SKU it still remembers must not become a
        // result, because everything shown is re-read from the database.
        stub.willReturn("GONE-001", "NIK-HD-001");
        var results = search("warm layer", null, null, null, null, false);

        assertThat(results.content())
                .extracting(ProductSummaryResponse::sku)
                .containsExactly("NIK-HD-001");
    }

    // --- degrading safely ---------------------------------------------------

    @Test
    void fallsBackToKeywordSearchWhenTheIndexIsNotReady() {
        stub.isUnavailable();

        var results = search("t-shirt", "Nike", null, null, null, false);

        assertThat(results.content()).hasSize(4);
        assertThat(stub.queriesSeen()).isEmpty();
    }

    @Test
    void fallsBackToKeywordSearchWhenRetrievalFindsNothing() {
        stub.willReturnNothing();

        var results = search("t-shirt", "Nike", null, null, null, false);

        assertThat(results.content()).hasSize(4);
    }

    @Test
    void fallsBackWhenTheFiltersRejectEveryCandidate() {
        // Retrieval found warm things; all of them cost too much. Rather than an
        // empty page, the keyword path gets a turn.
        stub.willReturn("UNQ-JK-001", "ZAR-JK-001");
        var results = search("t-shirt", "Nike", null, null, BigDecimal.valueOf(2000), false);

        assertThat(results.content()).isNotEmpty();
        assertThat(results.content())
                .allSatisfy(p -> assertThat(p.brand()).isEqualTo("Nike"));
    }

    @Test
    void aSearchWithNoTextSkipsRetrievalEntirely() {
        stub.willReturn("NIK-HD-001");
        var results = search(null, "Nike", null, null, null, false);

        assertThat(stub.queriesSeen()).isEmpty();
        assertThat(results.content()).allSatisfy(p ->
                assertThat(p.brand()).isEqualTo("Nike"));
    }

    // --- paging over ranked results -----------------------------------------

    @Test
    void pagesRankedResultsWithoutRepeatingOne() {
        stub.willReturn("NIK-HD-001", "ADI-HD-001", "UNQ-JK-001", "ZAR-JK-001");

        var first = productService.search(new ProductSearchCriteria(
                "warm", null, null, null, null, false, ProductSort.RELEVANCE, null, 0, 2));
        var second = productService.search(new ProductSearchCriteria(
                "warm", null, null, null, null, false, ProductSort.RELEVANCE, null, 1, 2));

        assertThat(first.content()).extracting(ProductSummaryResponse::sku)
                .containsExactly("NIK-HD-001", "ADI-HD-001");
        assertThat(second.content()).extracting(ProductSummaryResponse::sku)
                .containsExactly("UNQ-JK-001", "ZAR-JK-001");
        assertThat(first.totalElements()).isEqualTo(4);
        assertThat(first.last()).isFalse();
        assertThat(second.last()).isTrue();
    }

    // --- helpers ------------------------------------------------------------

    private PageResponse<ProductSummaryResponse> search(
            String text, String brand, String category,
            BigDecimal minPrice, BigDecimal maxPrice, boolean inStockOnly) {

        return productService.search(new ProductSearchCriteria(
                text, brand, category, minPrice, maxPrice, inStockOnly,
                ProductSort.RELEVANCE, null, 0, 20));
    }

    private PageResponse<ProductSummaryResponse> keyword(String text) {
        stub.isUnavailable();
        var result = search(text, null, null, null, null, false);
        stub.reset();
        return result;
    }
}
