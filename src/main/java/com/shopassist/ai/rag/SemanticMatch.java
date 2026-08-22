package com.shopassist.ai.rag;

/**
 * One semantically similar product.
 *
 * @param sku   the product matched
 * @param score similarity, 0..1, higher is closer
 */
public record SemanticMatch(String sku, double score) {
}
