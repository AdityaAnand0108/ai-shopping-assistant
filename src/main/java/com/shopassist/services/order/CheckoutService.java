package com.shopassist.services.order;

import com.shopassist.dto.order.CreateDraftRequest;
import com.shopassist.dto.order.DraftResponse;
import com.shopassist.dto.order.OrderDetailResponse;
import com.shopassist.entity.order.Order;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Checkout for the browser, over the same machinery the assistant uses.
 *
 * <p>This adds no purchasing rules of its own. It exists so a page can reach
 * {@link PurchaseService} without the entity mapping escaping the transaction,
 * and so the two callers — a shopper clicking Place order and a model calling a
 * tool — cannot end up with different definitions of what a purchase is. The
 * two-step split is therefore inherited rather than re-implemented: this class
 * has no method that both prices a basket and places the order.
 */
@Service
public class CheckoutService {

    private final PurchaseService purchaseService;
    private final OrderService orderService;

    public CheckoutService(PurchaseService purchaseService, OrderService orderService) {
        this.purchaseService = purchaseService;
        this.orderService = orderService;
    }

    /**
     * Prices a basket. Creates no order.
     */
    @Transactional
    public DraftResponse draft(CreateDraftRequest request) {
        // No conversation: a basket from the checkout page is confirmed by the
        // reference the page holds, never by recency.
        return DraftResponse.from(purchaseService.createDraft(quantityBySku(request), null));
    }

    /**
     * Places the order for one previously drafted basket.
     *
     * <p>Takes the reference explicitly rather than resolving the newest draft
     * the way the assistant's tool does. A page holds the reference it was given
     * and can hand it straight back, so there is no reason to guess — and doing
     * so would mean a shopper with two tabs open could confirm the basket from
     * the wrong one.
     */
    @Transactional
    public OrderDetailResponse confirm(String reference) {
        Order placed = purchaseService.confirmDraft(reference);
        // Read the finished order back through the same path the orders page
        // uses, so a freshly placed order and one opened later cannot describe
        // themselves differently. It re-checks ownership too, at no cost.
        return orderService.myOrder(placed.getOrderNumber());
    }

    /** A priced basket, for a page that wants to show it before confirming. */
    @Transactional(readOnly = true)
    public DraftResponse view(String reference) {
        return DraftResponse.from(purchaseService.draft(reference));
    }

    @Transactional
    public void cancel(String reference) {
        purchaseService.cancelDraft(reference);
    }

    /**
     * Collapses the requested lines into one quantity per SKU.
     *
     * <p>A basket that names the same product twice is a client bug, but summing
     * is the only reading that cannot silently lose an item — and letting the
     * lines overwrite each other in a map would do exactly that.
     */
    private static Map<String, Integer> quantityBySku(CreateDraftRequest request) {
        Map<String, Integer> merged = new LinkedHashMap<>();
        for (CreateDraftRequest.Line line : request.items()) {
            // SKUs are matched case-insensitively downstream, so they are
            // folded here too — otherwise "abc" and "ABC" would survive as two
            // lines for one product.
            merged.merge(line.sku().toUpperCase(Locale.ROOT), line.quantity(), Integer::sum);
        }
        return merged;
    }
}
