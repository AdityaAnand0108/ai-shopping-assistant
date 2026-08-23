package com.shopassist.dto.order;

import com.shopassist.entity.order.OrderDraft;
import com.shopassist.enums.order.DraftStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * A priced proposal. No order exists yet and nothing has been charged.
 *
 * <p>{@code reference} is what the checkout page holds on to and sends back to
 * confirm. It is a random UUID rather than the row id, and every lookup that
 * uses it is also scoped to the signed-in user, so guessing one buys nothing.
 *
 * <p>{@code expiresAt} is included because the shopper deserves to know the
 * quote has a shelf life — confirming after it lapses is refused rather than
 * silently repriced.
 */
public record DraftResponse(
        String reference,
        DraftStatus status,
        List<DraftItemResponse> items,
        BigDecimal totalAmount,
        String currency,
        Instant expiresAt
) {
    public static DraftResponse from(OrderDraft draft) {
        return new DraftResponse(
                draft.getPublicRef(),
                draft.getStatus(),
                draft.getItems().stream().map(DraftItemResponse::from).toList(),
                draft.getTotalAmount(),
                draft.getCurrency(),
                draft.getExpiresAt());
    }
}
