package com.shopassist.dto.catalog;

import java.util.List;

/**
 * The values a client can legitimately filter on, so the frontend builds its
 * dropdowns from the catalog rather than from a hardcoded list that drifts.
 */
public record CatalogFiltersResponse(
        List<String> brands,
        List<String> categories
) {
}
