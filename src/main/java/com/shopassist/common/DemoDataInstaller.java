package com.shopassist.common;

import com.shopassist.catalog.Product;
import com.shopassist.catalog.ProductCsvLoader;
import com.shopassist.catalog.ProductRepository;
import com.shopassist.order.Order;
import com.shopassist.order.OrderEvent;
import com.shopassist.order.OrderItem;
import com.shopassist.order.OrderRepository;
import com.shopassist.order.OrderStatus;
import com.shopassist.user.AppUser;
import com.shopassist.user.AppUserRepository;
import com.shopassist.user.UserRole;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Installs the demo dataset: the catalog from CSV, plus a handful of accounts
 * with deliberately varied order histories.
 *
 * <p>The accounts are not interchangeable. Two of them hold real orders so the
 * cross-account guardrail can actually be demonstrated (sign in as one, ask
 * about the other's order number, get a clean not-found), and one holds no
 * orders at all so the empty state gets exercised too.
 *
 * <p>Seeding is idempotent: each section is skipped if its table already has
 * rows, so restarting against the file-backed database does not duplicate data.
 *
 * <p>Deliberately not an {@code ApplicationRunner} itself — see
 * {@link DataSeeder}. Installing on demand lets a test call this inside its own
 * transaction and have it rolled back, which a startup runner cannot offer.
 */
@Component
@Slf4j
public class DemoDataInstaller {

    private static final String CURRENCY = "INR";
    private static final int ORDER_NUMBER_START = 101;
    private static final Duration STEP = Duration.ofHours(18);

    private final SeedProperties properties;
    private final ProductCsvLoader csvLoader;
    private final ProductRepository productRepository;
    private final AppUserRepository userRepository;
    private final OrderRepository orderRepository;
    private final PasswordEncoder passwordEncoder;

    public DemoDataInstaller(SeedProperties properties,
                             ProductCsvLoader csvLoader,
                             ProductRepository productRepository,
                             AppUserRepository userRepository,
                             OrderRepository orderRepository,
                             PasswordEncoder passwordEncoder) {
        this.properties = properties;
        this.csvLoader = csvLoader;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.orderRepository = orderRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public void install() {
        seedProducts();
        Map<String, AppUser> users = seedUsers();
        seedOrders(users);
    }

    private void seedProducts() {
        if (productRepository.count() > 0) {
            log.info("Catalog already populated ({} products); skipping", productRepository.count());
            return;
        }
        List<Product> products = csvLoader.load(properties.productsCsv());
        productRepository.saveAll(products);
        log.info("Seeded {} products", products.size());
    }

    private Map<String, AppUser> seedUsers() {
        Map<String, AppUser> users = new LinkedHashMap<>();
        for (DemoUser demo : DEMO_USERS) {
            AppUser user = userRepository.findByUsername(demo.username())
                    .orElseGet(() -> userRepository.save(AppUser.builder()
                            .username(demo.username())
                            .email(demo.email())
                            .fullName(demo.fullName())
                            .passwordHash(passwordEncoder.encode(demo.password()))
                            .role(UserRole.CUSTOMER)
                            .enabled(true)
                            .failedLoginAttempts(0)
                            .build()));
            users.put(demo.username(), user);
        }
        log.info("Seeded {} demo accounts: {}", users.size(), users.keySet());
        return users;
    }

    private void seedOrders(Map<String, AppUser> users) {
        if (orderRepository.count() > 0) {
            log.info("Orders already populated ({}); skipping", orderRepository.count());
            return;
        }

        Instant now = Instant.now();
        int sequence = ORDER_NUMBER_START;

        for (DemoOrder spec : DEMO_ORDERS) {
            AppUser user = users.get(spec.username());
            if (user == null) {
                continue;
            }

            Instant placedAt = now.minus(Duration.ofDays(spec.placedDaysAgo()));
            Order order = Order.builder()
                    .orderNumber("ORD-2026-%06d".formatted(sequence++))
                    .user(user)
                    .status(spec.status())
                    .placedAt(placedAt)
                    .expectedDeliveryDate(placedAt.plus(Duration.ofDays(6))
                            .atZone(ZoneOffset.UTC).toLocalDate())
                    .totalAmount(BigDecimal.ZERO)
                    .currency(CURRENCY)
                    .shippingAddress(shippingAddressFor(spec.username()))
                    .build();

            BigDecimal total = BigDecimal.ZERO;
            for (DemoLine line : spec.lines()) {
                Product product = productRepository.findBySkuIgnoreCase(line.sku())
                        .orElseThrow(() -> new IllegalStateException(
                                "Demo order references unknown SKU: " + line.sku()));
                BigDecimal lineTotal = product.getPrice()
                        .multiply(BigDecimal.valueOf(line.quantity()));
                order.addItem(OrderItem.builder()
                        .product(product)
                        .quantity(line.quantity())
                        .unitPrice(product.getPrice())
                        .lineTotal(lineTotal)
                        .build());
                total = total.add(lineTotal);
            }
            order.setTotalAmount(total);

            applyTimeline(order, placedAt);
            orderRepository.save(order);
        }

        log.info("Seeded {} demo orders", DEMO_ORDERS.size());
    }

    /**
     * Writes the tracking timeline that led to the order's current status, and
     * back-fills the matching completion timestamp.
     */
    private void applyTimeline(Order order, Instant placedAt) {
        List<OrderStatus> sequence = timelineFor(order.getStatus());
        Instant at = placedAt;

        for (OrderStatus status : sequence) {
            order.addEvent(OrderEvent.builder()
                    .status(status)
                    .occurredAt(at)
                    .note(NOTES.get(status))
                    .build());
            at = at.plus(STEP);
        }

        Instant finalEventAt = order.getEvents().get(order.getEvents().size() - 1).getOccurredAt();
        switch (order.getStatus()) {
            case DELIVERED -> order.setDeliveredAt(finalEventAt);
            case RETURNED -> order.setDeliveredAt(finalEventAt.minus(STEP));
            case CANCELLED -> {
                order.setCancelledAt(finalEventAt);
                order.setExpectedDeliveryDate(null);
            }
            default -> {
                // Still in flight: the recorded ETA stands.
            }
        }
    }

    private static List<OrderStatus> timelineFor(OrderStatus status) {
        List<OrderStatus> happyPath = List.of(
                OrderStatus.PLACED, OrderStatus.CONFIRMED, OrderStatus.PACKED,
                OrderStatus.SHIPPED, OrderStatus.OUT_FOR_DELIVERY, OrderStatus.DELIVERED);

        return switch (status) {
            case CANCELLED -> List.of(OrderStatus.PLACED, OrderStatus.CONFIRMED, OrderStatus.CANCELLED);
            case RETURNED -> {
                List<OrderStatus> withReturn = new ArrayList<>(happyPath);
                withReturn.add(OrderStatus.RETURNED);
                yield withReturn;
            }
            default -> happyPath.subList(0, happyPath.indexOf(status) + 1);
        };
    }

    private static String shippingAddressFor(String username) {
        return switch (username) {
            case "aditya" -> "14 Nehru Park Road, Koramangala, Bengaluru 560034";
            case "priya" -> "302 Sunrise Apartments, Baner, Pune 411045";
            case "rahul" -> "77 Salt Lake Sector V, Kolkata 700091";
            default -> "1 Demo Street, Sample City 000001";
        };
    }

    private static final Map<OrderStatus, String> NOTES = Map.of(
            OrderStatus.PLACED, "Order received and payment authorised",
            OrderStatus.CONFIRMED, "Seller confirmed availability",
            OrderStatus.PACKED, "Item packed at the fulfilment centre",
            OrderStatus.SHIPPED, "Handed over to the courier partner",
            OrderStatus.OUT_FOR_DELIVERY, "Out for delivery with the local hub",
            OrderStatus.DELIVERED, "Delivered and signed for",
            OrderStatus.CANCELLED, "Cancelled at the customer's request; refund initiated",
            OrderStatus.RETURNED, "Return picked up and refund processed"
    );

    // --- Demo dataset definition -------------------------------------------

    private record DemoUser(String username, String email, String fullName, String password) {
    }

    private record DemoLine(String sku, int quantity) {
    }

    private record DemoOrder(String username, OrderStatus status, int placedDaysAgo, List<DemoLine> lines) {
    }

    private static final List<DemoUser> DEMO_USERS = List.of(
            new DemoUser("aditya", "aditya@example.com", "Aditya Ambekar", "Password123"),
            new DemoUser("priya", "priya@example.com", "Priya Nair", "Password123"),
            new DemoUser("rahul", "rahul@example.com", "Rahul Sharma", "Password123"),
            new DemoUser("demo", "demo@example.com", "Demo Shopper", "Demo1234")
    );

    private static final List<DemoOrder> DEMO_ORDERS = List.of(
            new DemoOrder("aditya", OrderStatus.DELIVERED, 28,
                    List.of(new DemoLine("NIK-SH-001", 1), new DemoLine("NIK-TS-002", 2))),
            new DemoOrder("aditya", OrderStatus.OUT_FOR_DELIVERY, 4,
                    List.of(new DemoLine("SNY-HP-001", 1))),
            new DemoOrder("aditya", OrderStatus.SHIPPED, 6,
                    List.of(new DemoLine("LEV-JN-001", 1), new DemoLine("JJ-TR-001", 1))),
            new DemoOrder("aditya", OrderStatus.PLACED, 1,
                    List.of(new DemoLine("BK-001", 1), new DemoLine("BK-002", 1))),
            new DemoOrder("aditya", OrderStatus.RETURNED, 60,
                    List.of(new DemoLine("FIR-WT-001", 1))),

            new DemoOrder("priya", OrderStatus.DELIVERED, 15,
                    List.of(new DemoLine("UNQ-TS-001", 3))),
            new DemoOrder("priya", OrderStatus.OUT_FOR_DELIVERY, 2,
                    List.of(new DemoLine("PRE-KT-002", 1), new DemoLine("MIL-KT-001", 1))),
            new DemoOrder("priya", OrderStatus.CONFIRMED, 1,
                    List.of(new DemoLine("APL-WT-001", 1))),

            new DemoOrder("rahul", OrderStatus.CANCELLED, 10,
                    List.of(new DemoLine("XIA-PH-001", 1)))
    );
}
