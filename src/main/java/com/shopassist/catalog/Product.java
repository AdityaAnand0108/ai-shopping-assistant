package com.shopassist.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A catalog item. Seeded from {@code classpath:data/products.csv}.
 *
 * <p>{@link #sku} is the public identifier used in APIs, tool calls and RAG
 * citations; {@link #id} stays internal.
 */
@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40, unique = true)
    private String sku;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, length = 80)
    private String brand;

    @Column(nullable = false, length = 60)
    private String category;

    @Column(length = 60)
    private String subcategory;

    @Column(length = 40)
    private String color;

    /** Size as shown to shoppers: "M", "32", "5L". Column is not named "size"
     *  because that word is awkward across MySQL and H2. */
    @Column(name = "size_label", length = 30)
    private String sizeLabel;

    @Column(length = 60)
    private String material;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "stock_quantity", nullable = false)
    private int stockQuantity;

    @Column(precision = 2, scale = 1)
    private BigDecimal rating;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public boolean isInStock() {
        return stockQuantity > 0;
    }

    /**
     * The text that gets embedded for semantic search in Phase 6. Kept on the
     * entity so the indexing pipeline and any re-index job cannot drift apart.
     */
    public String toEmbeddableText() {
        StringBuilder sb = new StringBuilder()
                .append(name).append('\n')
                .append("Brand: ").append(brand).append('\n')
                .append("Category: ").append(category);
        if (subcategory != null && !subcategory.isBlank()) {
            sb.append(" / ").append(subcategory);
        }
        sb.append('\n');
        if (color != null && !color.isBlank()) {
            sb.append("Colour: ").append(color).append('\n');
        }
        if (material != null && !material.isBlank()) {
            sb.append("Material: ").append(material).append('\n');
        }
        if (description != null && !description.isBlank()) {
            sb.append(description);
        }
        return sb.toString();
    }
}
