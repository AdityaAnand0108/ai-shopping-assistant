package com.shopassist.services.order;

import com.shopassist.entity.catalog.Product;
import com.shopassist.entity.order.Order;
import com.shopassist.entity.order.OrderDraft;
import com.shopassist.entity.order.OrderDraftItem;
import com.shopassist.entity.order.OrderEvent;
import com.shopassist.entity.order.OrderItem;
import com.shopassist.entity.user.AppUser;
import com.shopassist.enums.order.DraftStatus;
import com.shopassist.enums.order.OrderStatus;
import com.shopassist.exception.InvalidRequestException;
import com.shopassist.exception.ResourceNotFoundException;
import com.shopassist.repository.catalog.ProductRepository;
import com.shopassist.repository.order.OrderDraftRepository;
import com.shopassist.repository.order.OrderRepository;
import com.shopassist.security.CurrentUserService;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Building and confirming purchases.
 *
 * <p>The two-step split is the safety property of this whole project: no single
 * call both decides on a purchase and completes it. {@link #createDraft} prices
 * a proposal and stops. {@link #confirmDraft} is the only thing that creates an
 * order, and it re-validates everything rather than trusting the draft — because
 * between the two calls, stock can sell out and prices can move.
 */
@Service
@Slf4j
public class PurchaseService {

    /** How long a priced proposal stays valid. */
    static final Duration DRAFT_LIFETIME = Duration.ofMinutes(15);

    /** Most units of one product in a single order. */
    static final int MAX_QUANTITY_PER_LINE = 10;

    /** Most distinct products in a single order. */
    static final int MAX_LINES = 10;

    private static final String CURRENCY = "USD";
    private static final int DELIVERY_DAYS = 6;

    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final OrderDraftRepository draftRepository;
    private final CurrentUserService currentUserService;

    public PurchaseService(ProductRepository productRepository,
                           OrderRepository orderRepository,
                           OrderDraftRepository draftRepository,
                           CurrentUserService currentUserService) {
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.draftRepository = draftRepository;
        this.currentUserService = currentUserService;
    }

    /**
     * Prices a proposed purchase. Creates no order and takes no payment.
     *
     * @param conversationRef the thread this was proposed in, or null when it
     *                        came from the checkout page. It is what scopes a
     *                        later {@link #confirmLatestDraftIn} to the right
     *                        purchase.
     */
    @Transactional
    public OrderDraft createDraft(Map<String, Integer> quantityBySku, String conversationRef) {
        if (quantityBySku == null || quantityBySku.isEmpty()) {
            throw new InvalidRequestException("A purchase needs at least one item");
        }
        if (quantityBySku.size() > MAX_LINES) {
            throw new InvalidRequestException(
                    "A single order can contain at most " + MAX_LINES + " different products");
        }

        AppUser user = currentUserService.requireUser();
        OrderDraft draft = OrderDraft.builder()
                .user(user)
                .conversationRef(conversationRef)
                .status(DraftStatus.PENDING)
                .currency(CURRENCY)
                .totalAmount(BigDecimal.ZERO)
                .expiresAt(Instant.now().plus(DRAFT_LIFETIME))
                .build();

        BigDecimal total = BigDecimal.ZERO;
        for (Map.Entry<String, Integer> line : quantityBySku.entrySet()) {
            int quantity = validatedQuantity(line.getValue());
            Product product = productRepository.findBySkuIgnoreCase(line.getKey())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "No product with SKU " + line.getKey()));

            if (product.getStockQuantity() < quantity) {
                throw new InvalidRequestException(
                        "%s is not available in the quantity requested".formatted(product.getName()));
            }

            BigDecimal lineTotal = product.getPrice().multiply(BigDecimal.valueOf(quantity));
            draft.addItem(OrderDraftItem.builder()
                    .product(product)
                    .quantity(quantity)
                    .unitPrice(product.getPrice())
                    .lineTotal(lineTotal)
                    .build());
            total = total.add(lineTotal);
        }

        draft.setTotalAmount(total);
        OrderDraft saved = draftRepository.save(draft);
        log.info("Drafted purchase {} for '{}' totalling {}",
                saved.getPublicRef(), user.getUsername(), total);
        return saved;
    }

    /**
     * Confirms the shopper's most recent proposed purchase.
     *
     * <p>This is what the assistant calls, and it takes no reference at all.
     *
     * <p>It used to take one, and that was a design mistake serious enough to
     * break the purchase flow entirely. Conversation history replays only the
     * text of previous turns, so a reference the model received from a tool in
     * one turn is gone by the next. Asked to confirm, the model had nothing to
     * pass — so it either invented a reference or simply drafted the purchase
     * again, and a shopper saying "yes, place the order" could loop forever
     * without ever buying anything.
     *
     * <p>Resolving the draft server-side removes the whole class of failure, and
     * it follows the same principle as {@code listMyOrders}: the model should
     * never have to carry an identifier it can get wrong. The two-step guarantee
     * is untouched — a draft must still have been built in an earlier call, so
     * no single call both decides on a purchase and completes it.
     *
     * <p>Scoped to one conversation, which it was not originally. "The shopper's
     * most recent draft" is the right answer inside a thread and the wrong one
     * across two: a draft left unconfirmed in an earlier conversation was the
     * newest draft everywhere, so a shopper agreeing to a purchase in a later
     * conversation could confirm that earlier one instead. Observed in a log
     * where a $1,099 draft from one thread was the target of a confirm in
     * another thread about $129 shoes; it failed for an unrelated reason.
     */
    @Transactional
    public Order confirmLatestDraftIn(String conversationRef) {
        if (conversationRef == null) {
            // Only a chat turn has a conversation, and only a chat turn calls
            // this. Refusing is safer than falling back to "the newest draft
            // anywhere", which is the behaviour this replaced.
            throw new InvalidRequestException(
                    "There is nothing to confirm yet. Price the purchase first.");
        }

        OrderDraft draft = draftRepository
                .findFirstByUserIdAndConversationRefOrderByIdDesc(
                        currentUserService.requireUserId(), conversationRef)
                .orElseThrow(() -> new InvalidRequestException(
                        "There is nothing to confirm in this conversation. Build the "
                                + "purchase first with createOrderDraft, tell the shopper "
                                + "the total, and then confirm it."));

        return confirm(draft);
    }

    /**
     * Confirms one specific draft by reference.
     *
     * <p>Not reachable from the assistant, which uses {@link #confirmLatestDraftIn}.
     * Kept for a UI that shows a drafted purchase and lets the shopper click it,
     * where the reference is carried by the page rather than recalled by a model.
     */
    @Transactional
    public Order confirmDraft(String draftRef) {
        OrderDraft draft = draftRepository
                .findByPublicRefAndUserId(draftRef, currentUserService.requireUserId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No pending purchase found with reference " + draftRef));

        return confirm(draft);
    }

    /**
     * Turns a draft into a real order.
     *
     * <p>Re-reads prices and stock rather than trusting what the draft recorded.
     * A draft is a quote, and a quote taken minutes ago is not a promise the shop
     * can still keep.
     */
    private Order confirm(OrderDraft draft) {
        if (draft.getStatus() == DraftStatus.CONFIRMED) {
            // Re-confirming is a no-op, not a second order. A model that repeats
            // a tool call must not be able to charge twice.
            log.info("Draft {} was already confirmed; returning the existing order",
                    draft.getPublicRef());
            return draft.getConfirmedOrder();
        }
        if (draft.getStatus() == DraftStatus.CANCELLED) {
            throw new InvalidRequestException("That purchase was cancelled and cannot be confirmed");
        }
        if (draft.isExpired()) {
            throw new InvalidRequestException(
                    "That purchase has expired. Please build it again to get current prices");
        }

        Instant now = Instant.now();
        Order order = Order.builder()
                .orderNumber(nextOrderNumber())
                .user(draft.getUser())
                .status(OrderStatus.PLACED)
                .placedAt(now)
                .expectedDeliveryDate(now.plus(Duration.ofDays(DELIVERY_DAYS))
                        .atZone(ZoneOffset.UTC).toLocalDate())
                .totalAmount(BigDecimal.ZERO)
                .currency(CURRENCY)
                .shippingAddress(null)
                .build();

        BigDecimal total = BigDecimal.ZERO;
        for (OrderDraftItem line : draft.getItems()) {
            Product product = productRepository.findById(line.getProduct().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product no longer available"));

            if (product.getStockQuantity() < line.getQuantity()) {
                throw new InvalidRequestException(
                        "%s sold out before the order was confirmed".formatted(product.getName()));
            }

            // Charge the current price, not the quoted one.
            BigDecimal lineTotal = product.getPrice().multiply(BigDecimal.valueOf(line.getQuantity()));
            order.addItem(OrderItem.builder()
                    .product(product)
                    .quantity(line.getQuantity())
                    .unitPrice(product.getPrice())
                    .lineTotal(lineTotal)
                    .build());
            total = total.add(lineTotal);

            product.setStockQuantity(product.getStockQuantity() - line.getQuantity());
            productRepository.save(product);
        }

        order.setTotalAmount(total);
        order.addEvent(OrderEvent.builder()
                .status(OrderStatus.PLACED)
                .occurredAt(now)
                .note("Order received and payment authorised")
                .build());

        Order placed = orderRepository.save(order);
        draft.setStatus(DraftStatus.CONFIRMED);
        draft.setConfirmedOrder(placed);
        draftRepository.save(draft);

        log.info("Confirmed draft {} as order {} totalling {}",
                draft.getPublicRef(), placed.getOrderNumber(), total);
        return placed;
    }

    @Transactional
    public void cancelDraft(String draftRef) {
        OrderDraft draft = draftRepository
                .findByPublicRefAndUserId(draftRef, currentUserService.requireUserId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No pending purchase found with reference " + draftRef));

        if (draft.getStatus() == DraftStatus.CONFIRMED) {
            throw new InvalidRequestException(
                    "That purchase is already an order. Cancel the order instead");
        }
        draft.setStatus(DraftStatus.CANCELLED);
        draftRepository.save(draft);
    }

    /**
     * Cancels an order the shopper already placed, if its status still allows it.
     */
    @Transactional
    public Order cancelOrder(String orderNumber) {
        Order order = orderRepository
                .findByOrderNumberIgnoreCaseAndUserId(orderNumber, currentUserService.requireUserId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No order found with number " + orderNumber));

        if (!order.getStatus().isCancellable()) {
            throw new InvalidRequestException(
                    "Order %s is %s and can no longer be cancelled"
                            .formatted(order.getOrderNumber(),
                                    order.getStatus().name().toLowerCase().replace('_', ' ')));
        }

        Instant now = Instant.now();
        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelledAt(now);
        order.setExpectedDeliveryDate(null);
        order.addEvent(OrderEvent.builder()
                .status(OrderStatus.CANCELLED)
                .occurredAt(now)
                .note("Cancelled at the customer's request; refund initiated")
                .build());

        // Stock returns to the shelf.
        for (OrderItem item : order.getItems()) {
            Product product = item.getProduct();
            product.setStockQuantity(product.getStockQuantity() + item.getQuantity());
            productRepository.save(product);
        }

        return orderRepository.save(order);
    }

    private static int validatedQuantity(Integer quantity) {
        if (quantity == null || quantity < 1) {
            throw new InvalidRequestException("Quantity must be at least 1");
        }
        if (quantity > MAX_QUANTITY_PER_LINE) {
            throw new InvalidRequestException(
                    "At most " + MAX_QUANTITY_PER_LINE + " of any one item per order");
        }
        return quantity;
    }

    /**
     * Picks an unused order number. The random component keeps sequential order
     * numbers from disclosing how many orders the shop has taken.
     */
    private String nextOrderNumber() {
        for (int attempt = 0; attempt < 20; attempt++) {
            String candidate = "ORD-%d-%06d".formatted(
                    LocalDate.now(ZoneOffset.UTC).getYear(),
                    ThreadLocalRandom.current().nextInt(200_000, 999_999));
            if (!orderRepository.existsByOrderNumberIgnoreCase(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not allocate an order number");
    }

    /**
     * One of the shopper's drafts, scoped to them like every other lookup here.
     *
     * <p>Used to show a priced purchase back to the page that will confirm it.
     */
    @Transactional(readOnly = true)
    public OrderDraft draft(String draftRef) {
        OrderDraft draft = draftRepository
                .findByPublicRefAndUserId(draftRef, currentUserService.requireUserId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No pending purchase found with reference " + draftRef));
        // Touch the lines while the session is open; the caller maps them outside.
        draft.getItems().size();
        return draft;
    }

    /** Read-only view used by the assistant's tools. */
    @Transactional(readOnly = true)
    public List<OrderDraftItem> draftItems(String draftRef) {
        return draftRepository
                .findByPublicRefAndUserId(draftRef, currentUserService.requireUserId())
                .map(OrderDraft::getItems)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No pending purchase found with reference " + draftRef));
    }
}
