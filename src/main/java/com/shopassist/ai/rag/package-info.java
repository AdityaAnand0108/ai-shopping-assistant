/**
 * Semantic recall over the product catalog.
 *
 * <p>Search is hybrid: retrieval decides which products a phrase is
 * <em>about</em>, and SQL decides what may actually be shown. An embedding has
 * no idea what anything costs, so price, brand and stock filters run afterwards
 * against the candidate SKUs, and every result is re-read from the database —
 * a stale index can never surface an out-of-date price.
 *
 * <p>Retrieval is an enhancement, never a dependency. If the index is missing,
 * empty, or the embedding model is unreachable, search falls back to keywords.
 */
package com.shopassist.ai.rag;
