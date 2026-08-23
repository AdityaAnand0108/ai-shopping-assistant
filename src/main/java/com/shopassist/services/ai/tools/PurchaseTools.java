package com.shopassist.services.ai.tools;

import com.shopassist.entity.order.OrderDraft;
import com.shopassist.enums.order.OrderStatus;
import com.shopassist.exception.InvalidRequestException;
import com.shopassist.exception.ResourceNotFoundException;
import com.shopassist.services.order.PurchaseService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.shopassist.services.ai.guard.ToolCallRecorder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * Buying and cancelling.
 *
 * <p>These are the only tools that change anything, and the only ones that can
 * cost a shopper money — so the ability to do so is split across two calls that
 * cannot be made in one step.
 *
 * <p>{@link #createOrderDraft} prices a proposal and stops. {@link #confirmOrder}
 * is the only path to an actual order, and it takes a reference that only exists
 * because a draft was built first. A model that hallucinates a confirmation
 * reference gets a not-found; a model that is argued into buying something still
 * has to surface a total to the shopper and be told yes, because the draft is
 * where the price becomes visible.
 *
 * <p>This is a guardrail made of structure rather than instruction. The prompt
 * asks the model to confirm before buying; the two-call split is what happens
 * when the model ignores that.
 */
@Component
@Slf4j
public class PurchaseTools {

    private final PurchaseService purchaseService;
    private final ToolCallRecorder recorder;

    public PurchaseTools(PurchaseService purchaseService, ToolCallRecorder recorder) {
        this.purchaseService = purchaseService;
        this.recorder = recorder;
    }

    @Tool(name = "createOrderDraft", description = """
            Price a purchase the shopper is considering. This does NOT buy \
            anything and does NOT charge anyone — it returns a reference and a \
            total to show them. Always call this first, tell the shopper the exact \
            total, and wait for them to say yes before calling confirmOrder. \
            Supply items as SKU:quantity pairs separated by commas, for example \
            "NIK-TS-001:2, SNY-HP-001:1".""")
    public DraftSummary createOrderDraft(
            @ToolParam(description =
                    "Comma-separated SKU:quantity pairs, e.g. \"NIK-TS-001:2, BK-001:1\"")
            String items) {

        log.info("Tool createOrderDraft(items={})", items);

        OrderDraft draft;
        try {
            draft = purchaseService.createDraft(parseItems(items));
        } catch (ResourceNotFoundException e) {
            // Small models routinely mistype a SKU they are recalling from an
            // earlier turn. Spring AI hands this message back to the model, so
            // saying what to do next lets it recover inside the same turn
            // instead of reporting a dead end to the shopper. Deliberately no
            // fuzzy matching: guessing which product was meant is exactly the
            // kind of helpfulness that buys somebody the wrong thing.
            throw new ResourceNotFoundException(e.getMessage()
                    + ". Do not guess the SKU. Call searchProducts to find the product again,"
                    + " then call createOrderDraft with the SKU exactly as it was returned.");
        }

        return recorder.recorded("createOrderDraft", new DraftSummary(
                draft.getPublicRef(),
                draft.getItems().stream()
                        .map(i -> new DraftLine(i.getProduct().getSku(), i.getProduct().getName(),
                                i.getQuantity(), i.getUnitPrice(), i.getLineTotal()))
                        .toList(),
                draft.getTotalAmount(),
                draft.getCurrency(),
                draft.getExpiresAt(),
                "Nothing has been bought yet. Tell the shopper the total and ask "
                        + "them to confirm, then call confirmOrder with this reference."));
    }

    /**
     * Places the purchase most recently priced for this shopper.
     *
     * <p>Deliberately takes no arguments. It used to take the reference returned
     * by {@code createOrderDraft}, which could not work: conversation history
     * replays only the text of earlier turns, so that reference is gone by the
     * time the shopper says yes. The model was left either inventing one or
     * re-pricing the purchase forever.
     *
     * <p>Like {@code listMyOrders}, the absence of an argument is the point —
     * there is nothing here for the model to get wrong.
     */
    @Tool(name = "confirmOrder", description = """
            Place the purchase you most recently priced with createOrderDraft. \
            Takes no arguments: the store already knows which purchase is \
            waiting, so you never need a reference and must not ask the shopper \
            for one. Call this as soon as they agree to the total, and do not \
            price the purchase again first. Only call it after they have clearly \
            said yes. This charges the shopper and cannot be undone except by \
            cancelling the resulting order.""")
    public PlacedOrder confirmOrder() {
        log.info("Tool confirmOrder()");
        var order = purchaseService.confirmLatestDraft();

        return recorder.recorded("confirmOrder", new PlacedOrder(
                order.getOrderNumber(), order.getStatus(),
                order.getTotalAmount(), order.getExpectedDeliveryDate(),
                "Order placed successfully."));
    }

    @Tool(name = "cancelOrder", description = """
            Cancel one of the shopper's existing orders. Only works while an order \
            has not yet shipped; if it reports that the order can no longer be \
            cancelled, tell the shopper exactly that rather than promising to \
            handle it another way.""")
    public CancelledOrder cancelOrder(
            @ToolParam(description = "The order number to cancel, e.g. ORD-2026-000104")
            String orderNumber) {

        log.info("Tool cancelOrder(orderNumber={})", orderNumber);
        var order = purchaseService.cancelOrder(orderNumber);
        return recorder.recorded("cancelOrder", new CancelledOrder(
                order.getOrderNumber(), order.getStatus(),
                "Order cancelled. A refund has been initiated."));
    }

    /**
     * Parses the SKU:quantity string the model supplies.
     *
     * <p>A flat string rather than a structured list because small local models
     * produce malformed nested JSON arguments far more often than they mistype a
     * simple delimited string. Parsing strictly here — and rejecting anything
     * unclear — is more reliable than hoping for well-formed JSON.
     */
    static Map<String, Integer> parseItems(String items) {
        if (items == null || items.isBlank()) {
            throw new InvalidRequestException("A purchase needs at least one item");
        }

        Map<String, Integer> quantityBySku = new LinkedHashMap<>();
        for (String pair : items.split(",")) {
            String entry = pair.strip();
            if (entry.isEmpty()) {
                continue;
            }

            String[] parts = entry.split(":");
            if (parts.length != 2) {
                throw new InvalidRequestException(
                        "Each item must be written as SKU:quantity, for example NIK-TS-001:2");
            }

            String sku = parts[0].strip();
            if (sku.isEmpty()) {
                throw new InvalidRequestException("Each item must name a product SKU");
            }

            int quantity;
            try {
                quantity = Integer.parseInt(parts[1].strip());
            } catch (NumberFormatException e) {
                throw new InvalidRequestException(
                        "Quantity for " + sku + " must be a whole number");
            }

            // Merging rather than overwriting, so "NIK-TS-001:1, NIK-TS-001:2"
            // buys three rather than silently two.
            quantityBySku.merge(sku, quantity, Integer::sum);
        }

        if (quantityBySku.isEmpty()) {
            throw new InvalidRequestException("A purchase needs at least one item");
        }
        return quantityBySku;
    }

    // --- what the model sees ------------------------------------------------

    public record DraftLine(String sku, String name, int quantity,
                            BigDecimal unitPrice, BigDecimal lineTotal) {
    }

    public record DraftSummary(String draftReference, List<DraftLine> items,
                               BigDecimal total, String currency, Instant expiresAt,
                               String nextStep) {
    }

    public record PlacedOrder(String orderNumber, OrderStatus status, BigDecimal total,
                              LocalDate expectedDeliveryDate, String outcome) {
    }

    public record CancelledOrder(String orderNumber, OrderStatus status, String outcome) {
    }
}
