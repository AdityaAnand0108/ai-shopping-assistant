package com.shopassist.dto.order;

import com.shopassist.entity.order.OrderItem;
import java.math.BigDecimal;

/**
 * One line of an order as shown to its owner.
 *
 * <p>Carries the price actually paid, taken from the order line rather than
 * looked up live, so a later price change never rewrites what a shopper sees
 * they were charged.
 */
public record OrderItemResponse(
        String sku,
        String name,
        String brand,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal lineTotal,
        String imageUrl
) {
    public static OrderItemResponse from(OrderItem item) {
        return new OrderItemResponse(
                item.getProduct().getSku(),
                item.getProduct().getName(),
                item.getProduct().getBrand(),
                item.getQuantity(),
                item.getUnitPrice(),
                item.getLineTotal(),
                item.getProduct().getImageUrl());
    }
}
