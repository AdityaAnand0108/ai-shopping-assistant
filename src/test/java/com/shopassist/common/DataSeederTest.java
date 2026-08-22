package com.shopassist.common;

import com.shopassist.order.Order;
import com.shopassist.order.OrderEvent;
import com.shopassist.order.OrderRepository;
import com.shopassist.order.OrderStatus;
import com.shopassist.user.AppUser;
import com.shopassist.user.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the demo dataset the rest of the project is demonstrated against.
 *
 * <p>Seeding is switched on for this class only, so the assertions describe the
 * real startup path rather than a hand-built fixture.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "shopassist.seed.enabled=true")
@Transactional
class DataSeederTest {

    @Autowired
    private DataSeeder seeder;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void seed() {
        seeder.run(null);
    }

    @Test
    void createsTheFourDemoAccounts() {
        assertThat(userRepository.findAll())
                .extracting(AppUser::getUsername)
                .containsExactlyInAnyOrder("aditya", "priya", "rahul", "demo");
    }

    @Test
    void storesPasswordsAsBcryptHashesNotPlaintext() {
        AppUser aditya = userRepository.findByUsername("aditya").orElseThrow();

        assertThat(aditya.getPasswordHash())
                .isNotEqualTo("Password123")
                .startsWith("$2");
        assertThat(passwordEncoder.matches("Password123", aditya.getPasswordHash())).isTrue();
        assertThat(passwordEncoder.matches("wrong", aditya.getPasswordHash())).isFalse();
    }

    @Test
    void assignsEveryUserARandomPublicReferenceRatherThanExposingThePrimaryKey() {
        List<AppUser> users = userRepository.findAll();

        assertThat(users).allSatisfy(user ->
                // Parses as a UUID, so it carries no ordering or count information
                // that a caller could use to probe for other accounts.
                assertThat(UUID.fromString(user.getPublicRef()).version()).isEqualTo(4));

        assertThat(users).extracting(AppUser::getPublicRef).doesNotHaveDuplicates();
    }

    @Test
    void givesTheDemoAccountNoOrdersSoTheEmptyStateIsExercised() {
        AppUser demo = userRepository.findByUsername("demo").orElseThrow();
        assertThat(orderRepository.findByUserIdOrderByPlacedAtDesc(demo.getId())).isEmpty();
    }

    @Test
    void spreadsOrdersAcrossUsersAndStatuses() {
        AppUser aditya = userRepository.findByUsername("aditya").orElseThrow();
        List<Order> orders = orderRepository.findByUserIdOrderByPlacedAtDesc(aditya.getId());

        assertThat(orders).hasSize(5);
        assertThat(orders).extracting(Order::getStatus)
                .contains(OrderStatus.DELIVERED, OrderStatus.OUT_FOR_DELIVERY,
                        OrderStatus.SHIPPED, OrderStatus.PLACED, OrderStatus.RETURNED);
        assertThat(orders).isSortedAccordingTo(
                Comparator.comparing(Order::getPlacedAt).reversed());
    }

    @Test
    void scopesOrderLookupToTheOwningUser() {
        AppUser aditya = userRepository.findByUsername("aditya").orElseThrow();
        AppUser rahul = userRepository.findByUsername("rahul").orElseThrow();
        String adityasOrder = orderRepository
                .findByUserIdOrderByPlacedAtDesc(aditya.getId()).getFirst().getOrderNumber();

        // The owner sees it...
        assertThat(orderRepository.findByOrderNumberIgnoreCaseAndUserId(
                adityasOrder, aditya.getId())).isPresent();

        // ...and another signed-in user simply does not, which is the guarantee
        // the assistant's order tools will rely on in Phase 5.
        assertThat(orderRepository.findByOrderNumberIgnoreCaseAndUserId(
                adityasOrder, rahul.getId())).isEmpty();
    }

    @Test
    void computesOrderTotalFromItsLines() {
        AppUser priya = userRepository.findByUsername("priya").orElseThrow();
        Order tees = orderRepository.findByUserIdOrderByPlacedAtDesc(priya.getId()).stream()
                .filter(o -> o.getStatus() == OrderStatus.DELIVERED)
                .findFirst()
                .orElseThrow();

        BigDecimal expected = tees.getItems().stream()
                .map(item -> item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertThat(tees.getTotalAmount()).isEqualByComparingTo(expected);
        // 3 x Uniqlo AIRism tee at 990
        assertThat(tees.getTotalAmount()).isEqualByComparingTo("2970");
    }

    @Test
    void buildsAChronologicalTimelineEndingAtTheCurrentStatus() {
        Order delivered = anyOrderWithStatus(OrderStatus.DELIVERED);

        List<OrderEvent> events = delivered.getEvents();
        assertThat(events).extracting(OrderEvent::getStatus)
                .containsExactly(OrderStatus.PLACED, OrderStatus.CONFIRMED, OrderStatus.PACKED,
                        OrderStatus.SHIPPED, OrderStatus.OUT_FOR_DELIVERY, OrderStatus.DELIVERED);
        assertThat(events).isSortedAccordingTo(Comparator.comparing(OrderEvent::getOccurredAt));
        assertThat(events).allSatisfy(e -> assertThat(e.getNote()).isNotBlank());
    }

    @Test
    void recordsDeliveryTimestampOnlyForDeliveredOrders() {
        assertThat(anyOrderWithStatus(OrderStatus.DELIVERED).getDeliveredAt()).isNotNull();
        assertThat(anyOrderWithStatus(OrderStatus.SHIPPED).getDeliveredAt()).isNull();
    }

    @Test
    void clearsTheEtaOnACancelledOrderInsteadOfPromisingADate() {
        Order cancelled = anyOrderWithStatus(OrderStatus.CANCELLED);

        assertThat(cancelled.getCancelledAt()).isNotNull();
        assertThat(cancelled.getExpectedDeliveryDate()).isNull();
        assertThat(cancelled.getEvents()).extracting(OrderEvent::getStatus)
                .containsExactly(OrderStatus.PLACED, OrderStatus.CONFIRMED, OrderStatus.CANCELLED);
    }

    @Test
    void keepsAnEtaInTheFutureForOrdersStillInFlight() {
        Order inFlight = anyOrderWithStatus(OrderStatus.OUT_FOR_DELIVERY);

        assertThat(inFlight.getExpectedDeliveryDate()).isNotNull();
        assertThat(inFlight.getPlacedAt()).isBefore(Instant.now());
    }

    @Test
    void isIdempotentSoRestartsDoNotDuplicateData() {
        long usersAfterFirstRun = userRepository.count();
        long ordersAfterFirstRun = orderRepository.count();

        seeder.run(null);

        assertThat(userRepository.count()).isEqualTo(usersAfterFirstRun);
        assertThat(orderRepository.count()).isEqualTo(ordersAfterFirstRun);
    }

    private Order anyOrderWithStatus(OrderStatus status) {
        return orderRepository.findAll().stream()
                .filter(o -> o.getStatus() == status)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No seeded order with status " + status));
    }
}
