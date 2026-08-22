package com.shopassist.common;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * A page of results in a shape this API controls.
 *
 * <p>Spring's {@code Page} is not serialised directly on purpose: its JSON
 * structure is an implementation detail Spring itself warns against relying on,
 * and it carries the full {@code Pageable} and {@code Sort} internals along with
 * it. Declaring the contract here keeps the payload small and stable for the
 * Phase 10 frontend.
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {
    public static <E, T> PageResponse<T> from(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast());
    }
}
