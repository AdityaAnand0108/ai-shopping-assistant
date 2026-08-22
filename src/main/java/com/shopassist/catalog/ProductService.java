package com.shopassist.catalog;

import com.shopassist.common.PageResponse;
import com.shopassist.common.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Catalog reads.
 *
 * <p>Returns DTOs, never entities. From Phase 5 the assistant's product tools
 * call straight into this class, so whatever a tool can see is exactly what an
 * anonymous HTTP client can see — there is no privileged path that returns more.
 */
@Service
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public PageResponse<ProductSummaryResponse> search(ProductSearchCriteria criteria) {
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
