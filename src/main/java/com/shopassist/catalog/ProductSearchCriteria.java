package com.shopassist.catalog;

import com.shopassist.common.InvalidRequestException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;

/**
 * A normalised, bounded catalog query.
 *
 * <p>Every value is clamped or rejected here rather than trusted downstream.
 * That matters twice over: it stops a client from asking for the entire catalog
 * in one call, and from Phase 5 the same record carries arguments the language
 * model proposed. Validating in the record means the model's suggestions face
 * exactly the same limits as a hand-written HTTP request.
 */
public record ProductSearchCriteria(
        String text,
        String brand,
        String category,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        boolean inStockOnly,
        ProductSort sort,
        Sort.Direction direction,
        int page,
        int size
) {
    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 50;
    public static final int MAX_TEXT_LENGTH = 100;

    public ProductSearchCriteria {
        text = normalise(text, MAX_TEXT_LENGTH);
        brand = normalise(brand, 80);
        category = normalise(category, 60);

        if (minPrice != null && minPrice.signum() < 0) {
            throw new InvalidRequestException("minPrice cannot be negative");
        }
        if (maxPrice != null && maxPrice.signum() < 0) {
            throw new InvalidRequestException("maxPrice cannot be negative");
        }
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
            throw new InvalidRequestException("minPrice cannot be greater than maxPrice");
        }

        sort = sort == null ? ProductSort.RELEVANCE : sort;
        direction = direction == null ? sort.defaultDirection() : direction;
        page = Math.max(0, page);
        size = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
    }

    public Pageable toPageable() {
        return PageRequest.of(page, size, sort.toSort(direction));
    }

    /**
     * Trims, and drops a value that is blank once trimmed so it becomes a
     * dropped predicate rather than a search for the empty string. Over-long
     * input is truncated instead of rejected — a shopper who pastes a paragraph
     * into the search box should get results, not an error.
     */
    private static String normalise(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() > maxLength ? trimmed.substring(0, maxLength) : trimmed;
    }
}
