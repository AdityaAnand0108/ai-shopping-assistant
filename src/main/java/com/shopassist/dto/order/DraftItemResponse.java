package com.shopassist.dto.order;

import com.shopassist.entity.order.OrderDraftItem;
import java.math.BigDecimal;

/**
 * One priced line of a proposed purchase.
 *
 * <p>The prices here are the server's, read from the catalog when the draft was
 * built. A client that displayed its own cached price and quietly ignored this
 * would defeat the point of drafting at all.
 */
public record DraftItemResponse(
        String sku,
        String name,
        String brand,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal lineTotal,
        String imageUrl
) {
    public static DraftItemResponse from(OrderDraftItem item) {
        return new DraftItemResponse(
                item.getProduct().getSku(),
                item.getProduct().getName(),
                item.getProduct().getBrand(),
                item.getQuantity(),
                item.getUnitPrice(),
                item.getLineTotal(),
                item.getProduct().getImageUrl());
    }
}
