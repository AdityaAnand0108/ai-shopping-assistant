package com.shopassist.catalog;

import com.shopassist.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/**
 * Public catalog browsing. No token required, matching how a real storefront
 * lets anyone look before signing in.
 */
@RestController
@RequestMapping("/api/products")
@Tag(name = "Catalog", description = "Product search and detail")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    @Operation(summary = "Search the catalog with optional filters")
    public PageResponse<ProductSummaryResponse> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String brand,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(defaultValue = "false") boolean inStockOnly,
            @RequestParam(defaultValue = "RELEVANCE") ProductSort sort,
            @RequestParam(required = false) Sort.Direction direction,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        return productService.search(new ProductSearchCriteria(
                q, brand, category, minPrice, maxPrice, inStockOnly, sort, direction, page, size));
    }

    @GetMapping("/filters")
    @Operation(summary = "List the brands and categories available to filter on")
    public CatalogFiltersResponse filters() {
        return productService.filters();
    }

    @GetMapping("/{sku}")
    @Operation(summary = "Fetch one product by SKU")
    public ProductDetailResponse bySku(@PathVariable String sku) {
        return productService.findBySku(sku);
    }
}
