package com.shopassist.services.ai.tools;

import com.shopassist.dto.catalog.ProductSearchCriteria;
import com.shopassist.enums.catalog.Availability;
import com.shopassist.enums.catalog.ProductSort;
import com.shopassist.exception.InvalidRequestException;
import com.shopassist.services.catalog.ProductService;
import java.math.BigDecimal;
import java.util.List;
import com.shopassist.services.ai.guard.ToolCallRecorder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * Catalog lookups the assistant may perform.
 *
 * <p>Tool descriptions are written for the model, not for a developer: they say
 * when to reach for the tool and what it will not do, because that text is the
 * only guidance the model gets at the moment it decides.
 *
 * <p>Results are deliberately lean. Every field returned is re-read by the model
 * and costs context, and anything included here can end up quoted back to the
 * shopper — so these carry what is needed to answer a shopping question and
 * nothing more. Notably still absent, as everywhere else: stock counts.
 */
@Component
@Slf4j
public class CatalogTools {

    /** Cap on rows returned to the model, well below the API's own page cap. */
    private static final int MAX_RESULTS = 8;

    private final ProductService productService;
    private final ToolCallRecorder recorder;

    public CatalogTools(ProductService productService, ToolCallRecorder recorder) {
        this.productService = productService;
        this.recorder = recorder;
    }

    @Tool(name = "searchProducts", description = """
            Search the store's product catalog. Use this for any question about \
            what the store sells, what is available, or what something costs. \
            Never answer such a question from your own knowledge — only this tool \
            knows the real catalog. All arguments are optional: omit any filter \
            the shopper did not mention. Returns at most 8 matches.""")
    public ProductSearchResult searchProducts(
            @ToolParam(required = false, description =
                    "Free text describing the item, e.g. 'running shoes' or 't-shirt'")
            String query,

            @ToolParam(required = false, description =
                    "Exact brand name, e.g. 'Nike'. Only use a brand the shopper named.")
            String brand,

            @ToolParam(required = false, description =
                    "One of: Apparel, Footwear, Electronics, Home, Sports, Books")
            String category,

            @ToolParam(required = false, description = "Lowest acceptable price in rupees")
            Double minPrice,

            @ToolParam(required = false, description = "Highest acceptable price in rupees")
            Double maxPrice,

            @ToolParam(required = false, description =
                    "True to exclude items that are out of stock")
            Boolean inStockOnly) {

        log.info("Tool searchProducts(query={}, brand={}, category={}, min={}, max={}, inStock={})",
                query, brand, category, minPrice, maxPrice, inStockOnly);

        var criteria = new ProductSearchCriteria(
                query, brand, category,
                toDecimal(minPrice), toDecimal(maxPrice),
                Boolean.TRUE.equals(inStockOnly),
                ProductSort.RELEVANCE, null, 0, MAX_RESULTS);

        var page = productService.search(criteria);
        List<ProductMatch> matches = page.content().stream()
                .map(p -> new ProductMatch(p.sku(), p.name(), p.brand(), p.category(),
                        p.price(), p.availability()))
                .toList();

        return recorder.recorded("searchProducts",
                new ProductSearchResult(matches, page.totalElements(), matches.size()));
    }

    @Tool(name = "getProductDetails", description = """
            Fetch the full description of one product by its exact SKU, for when a \
            shopper asks about the material, colour, size or details of an item \
            already found through searchProducts.""")
    public ProductDetail getProductDetails(
            @ToolParam(description = "The product's SKU, exactly as returned by searchProducts")
            String sku) {

        log.info("Tool getProductDetails(sku={})", sku);
        var product = productService.findBySku(sku);
        return recorder.recorded("getProductDetails", new ProductDetail(
                product.sku(), product.name(), product.brand(),
                product.category(), product.description(), product.color(),
                product.size(), product.material(), product.price(),
                product.rating(), product.availability()));
    }

    @Tool(name = "checkStock", description = """
            Check whether a specific quantity of one product can be bought right \
            now. Call this before proposing a purchase. Answers only yes or no — \
            the store does not disclose exact stock levels, so never claim a \
            specific number of units is available.""")
    public StockAnswer checkStock(
            @ToolParam(description = "The product's SKU")
            String sku,

            @ToolParam(description = "How many units the shopper wants")
            Integer quantity) {

        log.info("Tool checkStock(sku={}, quantity={})", sku, quantity);
        if (quantity == null || quantity < 1) {
            throw new InvalidRequestException("Quantity must be at least 1");
        }
        // Confirms the SKU is real, so an invented one is reported as unknown
        // rather than silently as "unavailable".
        productService.findBySku(sku);
        return recorder.recorded("checkStock",
                new StockAnswer(sku, quantity, productService.canFulfil(sku, quantity)));
    }

    private static BigDecimal toDecimal(Double value) {
        return value == null ? null : BigDecimal.valueOf(value);
    }

    // --- what the model sees ------------------------------------------------

    public record ProductMatch(String sku, String name, String brand, String category,
                               BigDecimal price, Availability availability) {
    }

    public record ProductSearchResult(List<ProductMatch> matches, long totalMatching, int returned) {
    }

    public record ProductDetail(String sku, String name, String brand, String category,
                                String description, String colour, String size, String material,
                                BigDecimal price, BigDecimal rating, Availability availability) {
    }

    public record StockAnswer(String sku, int requestedQuantity, boolean available) {
    }
}
