package com.shopassist.enums.catalog;

/**
 * How available an item is, without disclosing the exact count.
 *
 * <p>Stock level is commercially sensitive and is not something a shopper needs.
 * Publishing a band instead of a number means the catalog API cannot be scraped
 * to reconstruct inventory or infer sales rates. When the assistant needs to
 * know whether a specific quantity can be bought, Phase 5 answers that with a
 * {@code checkStock} tool returning a yes or no — still never the figure.
 */
public enum Availability {

    IN_STOCK,
    LOW_STOCK,
    OUT_OF_STOCK;

    /** At or below this many units an item is reported as running low. */
    private static final int LOW_STOCK_THRESHOLD = 5;

    public static Availability of(int stockQuantity) {
        if (stockQuantity <= 0) {
            return OUT_OF_STOCK;
        }
        return stockQuantity <= LOW_STOCK_THRESHOLD ? LOW_STOCK : IN_STOCK;
    }

    public boolean isPurchasable() {
        return this != OUT_OF_STOCK;
    }
}
