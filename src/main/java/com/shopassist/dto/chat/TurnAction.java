package com.shopassist.dto.chat;

import com.shopassist.dto.order.DraftResponse;
import com.shopassist.dto.order.OrderDetailResponse;

/**
 * What a turn actually did to the shopper's basket or orders.
 *
 * <p>Separate from the reply text on purpose. The model narrates in prose, and
 * prose is where it goes wrong: it has been observed pricing one pair of shoes
 * and then describing two, and announcing an order without ever quoting the
 * number the shop assigned. Anything a shopper is asked to act on — a total to
 * agree to, an order to check — belongs here, where it comes from the tool
 * result rather than from the sentence written about it.
 *
 * <p>A client should render this and treat the prose as commentary.
 *
 * @param draft a purchase priced this turn and still waiting on a yes, or null
 * @param order an order actually placed this turn, or null
 */
public record TurnAction(
        DraftResponse draft,
        OrderDetailResponse order
) {
    /** Null when the turn changed nothing, so clients can test one field. */
    public static TurnAction of(DraftResponse draft, OrderDetailResponse order) {
        return draft == null && order == null ? null : new TurnAction(draft, order);
    }
}
