package com.shopassist.repository.catalog;

import com.shopassist.entity.catalog.Product;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Catalog queries.
 *
 * <p>Two query methods rather than one because search has two shapes. The
 * keyword path filters and pages in SQL. The hybrid path takes a set of SKUs
 * that semantic retrieval proposed and applies the same hard filters to them,
 * because price, brand and stock are facts rather than similarities and an
 * embedding knows nothing about any of them.
 */
public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findBySkuIgnoreCase(String sku);

    List<Product> findBySkuInIgnoreCase(Collection<String> skus);

    @Query("SELECT DISTINCT p.brand FROM Product p ORDER BY p.brand")
    List<String> findDistinctBrands();

    @Query("SELECT DISTINCT p.category FROM Product p ORDER BY p.category")
    List<String> findDistinctCategories();

    /**
     * Deterministic catalog filter. Every parameter is optional; a null simply
     * drops that predicate. Phase 6 layers semantic recall on top of this, but
     * the hard constraints (price, stock, brand) stay in SQL where they can be
     * relied on rather than inferred by a model.
     */
    @Query("""
            SELECT p FROM Product p
            WHERE (:brand IS NULL OR LOWER(p.brand) = LOWER(:brand))
              AND (:category IS NULL OR LOWER(p.category) = LOWER(:category))
              AND (:minPrice IS NULL OR p.price >= :minPrice)
              AND (:maxPrice IS NULL OR p.price <= :maxPrice)
              AND (:inStockOnly = FALSE OR p.stockQuantity > 0)
              AND (:text IS NULL
                   OR LOWER(p.name) LIKE LOWER(CONCAT('%', :text, '%'))
                   OR LOWER(p.description) LIKE LOWER(CONCAT('%', :text, '%'))
                   OR LOWER(p.subcategory) LIKE LOWER(CONCAT('%', :text, '%')))
            """)
    Page<Product> search(@Param("text") String text,
                         @Param("brand") String brand,
                         @Param("category") String category,
                         @Param("minPrice") BigDecimal minPrice,
                         @Param("maxPrice") BigDecimal maxPrice,
                         @Param("inStockOnly") boolean inStockOnly,
                         Pageable pageable);

    /**
     * The same hard filters, restricted to a set of SKUs.
     *
     * <p>Used by the hybrid path: retrieval proposes candidates, and this
     * decides which of them a shopper may actually be shown. Price, brand and
     * stock stay in SQL because they are facts, not similarities — an embedding
     * has no idea what something costs.
     */
    @Query("""
            SELECT p FROM Product p
            WHERE UPPER(p.sku) IN :skus
              AND (:brand IS NULL OR LOWER(p.brand) = LOWER(:brand))
              AND (:category IS NULL OR LOWER(p.category) = LOWER(:category))
              AND (:minPrice IS NULL OR p.price >= :minPrice)
              AND (:maxPrice IS NULL OR p.price <= :maxPrice)
              AND (:inStockOnly = FALSE OR p.stockQuantity > 0)
            """)
    List<Product> findBySkusWithFilters(@Param("skus") Collection<String> skus,
                                        @Param("brand") String brand,
                                        @Param("category") String category,
                                        @Param("minPrice") BigDecimal minPrice,
                                        @Param("maxPrice") BigDecimal maxPrice,
                                        @Param("inStockOnly") boolean inStockOnly);
}
