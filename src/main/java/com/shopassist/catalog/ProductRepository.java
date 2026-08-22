package com.shopassist.catalog;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

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
}
