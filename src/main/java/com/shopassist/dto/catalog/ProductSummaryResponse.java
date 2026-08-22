package com.shopassist.dto.catalog;

import com.shopassist.entity.catalog.Product;
import com.shopassist.enums.catalog.Availability;
import java.math.BigDecimal;

/**
 * A product as it appears in a result list.
 *
 * <p>Fields are whitelisted rather than derived from the entity, so a column
 * added to {@link Product} later cannot start appearing in responses by
 * accident. Notably absent: the primary key, the raw stock count, and the
 * audit timestamps.
 */
public record ProductSummaryResponse(
        String sku,
        String name,
        String brand,
        String category,
        String subcategory,
        BigDecimal price,
        String currency,
        BigDecimal rating,
        Availability availability,
        String imageUrl
) {
    public static ProductSummaryResponse from(Product product) {
        return new ProductSummaryResponse(
                product.getSku(),
                product.getName(),
                product.getBrand(),
                product.getCategory(),
                product.getSubcategory(),
                product.getPrice(),
                product.getCurrency(),
                product.getRating(),
                Availability.of(product.getStockQuantity()),
                product.getImageUrl());
    }
}
