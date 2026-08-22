package com.shopassist.enums.catalog;

import com.shopassist.entity.order.Order;
import org.springframework.data.domain.Sort;

/**
 * The orderings a client may ask for.
 *
 * <p>An allowlist rather than a free-text sort parameter. Passing a raw property
 * name through to Spring Data lets a caller sort by any field on the entity —
 * including {@code id}, {@code stockQuantity} or, in other designs, a password
 * hash — and observe the ordering to infer values it was never shown. Naming the
 * permitted orderings makes that impossible to express.
 */
public enum ProductSort {

    /** Best rated first, with a stable tiebreak so paging cannot repeat or skip rows. */
    RELEVANCE("rating", Sort.Direction.DESC),
    PRICE("price", Sort.Direction.ASC),
    RATING("rating", Sort.Direction.DESC),
    NAME("name", Sort.Direction.ASC);

    private final String property;
    private final Sort.Direction defaultDirection;

    ProductSort(String property, Sort.Direction defaultDirection) {
        this.property = property;
        this.defaultDirection = defaultDirection;
    }

    public Sort.Direction defaultDirection() {
        return defaultDirection;
    }

    /**
     * Always appends SKU as a final tiebreak. Without it, rows sharing a rating
     * or price have no defined order, and two requests for consecutive pages can
     * legitimately return the same product twice while omitting another.
     */
    public Sort toSort(Sort.Direction direction) {
        Sort.Direction resolved = direction == null ? defaultDirection : direction;
        return Sort.by(new Sort.Order(resolved, property).nullsLast())
                .and(Sort.by(Sort.Direction.ASC, "sku"));
    }
}
