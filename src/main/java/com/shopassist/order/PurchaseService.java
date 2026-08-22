package com.shopassist.order;

import com.shopassist.catalog.Product;
import com.shopassist.catalog.ProductRepository;
import com.shopassist.common.InvalidRequestException;
import com.shopassist.common.ResourceNotFoundException;
import com.shopassist.security.CurrentUserService;
import com.shopassist.user.AppUser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

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

    private static final String CURRENCY = "INR";
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
     */
    @Transactional
    public OrderDraft createDraft(Map<String, Integer> quantityBySku) {
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
     * Turns a confirmed draft into a real order.
     *
     * <p>Re-reads prices and stock rather than trusting what the draft recorded.
     * A draft is a quote, and a quote taken minutes ago is not a promise the shop
     * can still keep.
     */
    @Transactional
    public Order confirmDraft(String draftRef) {
        OrderDraft draft = draftRepository
                .findByPublicRefAndUserId(draftRef, currentUserService.requireUserId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No pending purchase found with reference " + draftRef));

        if (draft.getStatus() == DraftStatus.CONFIRMED) {
            // Re-confirming is a no-op, not a second order. A model that repeats
            // a tool call must not be able to charge twice.
            log.info("Draft {} was already confirmed; returning the existing order", draftRef);
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
                draftRef, placed.getOrderNumber(), total);
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
