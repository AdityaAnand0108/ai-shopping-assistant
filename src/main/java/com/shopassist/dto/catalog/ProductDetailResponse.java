package com.shopassist.dto.catalog;

import com.shopassist.entity.catalog.Product;
import com.shopassist.enums.catalog.Availability;
import java.math.BigDecimal;

/**
 * A single product page. Adds the descriptive attributes a shopper wants before
 * buying; still no primary key and still no raw stock count.
 */
public record ProductDetailResponse(
        String sku,
        String name,
        String brand,
        String category,
        String subcategory,
        String description,
        String color,
        String size,
        String material,
        BigDecimal price,
        String currency,
        BigDecimal rating,
        Availability availability,
        String imageUrl
) {
    public static ProductDetailResponse from(Product product) {
        return new ProductDetailResponse(
                product.getSku(),
                product.getName(),
                product.getBrand(),
                product.getCategory(),
                product.getSubcategory(),
                product.getDescription(),
                product.getColor(),
                product.getSizeLabel(),
                product.getMaterial(),
                product.getPrice(),
                product.getCurrency(),
                product.getRating(),
                Availability.of(product.getStockQuantity()),
                product.getImageUrl());
    }
}
