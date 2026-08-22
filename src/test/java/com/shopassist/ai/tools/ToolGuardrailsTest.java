package com.shopassist.ai.tools;

import com.shopassist.common.DemoDataInstaller;
import com.shopassist.common.InvalidRequestException;
import com.shopassist.common.ResourceNotFoundException;
import com.shopassist.order.OrderDraftRepository;
import com.shopassist.order.OrderStatus;
import com.shopassist.security.AppUserPrincipal;
import com.shopassist.user.AppUser;
import com.shopassist.user.AppUserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The tools the model can call, tested directly.
 *
 * <p>These are the assertions that matter most in the whole project. A prompt
 * asking the model to behave is not evidence of anything; what follows is
 * evidence, because it exercises the code that runs no matter what the model
 * decides to do.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ToolGuardrailsTest {

    @Autowired
    private CatalogTools catalogTools;

    @Autowired
    private OrderTools orderTools;

    @Autowired
    private PurchaseTools purchaseTools;

    @Autowired
    private DemoDataInstaller installer;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private OrderDraftRepository draftRepository;

    @BeforeEach
    void setUp() {
        installer.install();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // --- the structural guarantee -------------------------------------------

    @Test
    void noToolAcceptsACustomerArgument() {
        // The guarantee is structural, so assert it structurally: if someone
        // later adds a userId parameter to a tool, this fails immediately.
        List<Method> toolMethods = Arrays.stream(
                        new Class<?>[]{CatalogTools.class, OrderTools.class, PurchaseTools.class})
                .flatMap(type -> Arrays.stream(type.getDeclaredMethods()))
                .filter(m -> m.isAnnotationPresent(org.springframework.ai.tool.annotation.Tool.class))
                .toList();

        assertThat(toolMethods).hasSize(9);

        for (Method tool : toolMethods) {
            for (var parameter : tool.getParameters()) {
                assertThat(parameter.getName().toLowerCase())
                        .as("tool %s must not take a caller-supplied identity", tool.getName())
                        .doesNotContain("user")
                        .doesNotContain("customer")
                        .doesNotContain("account")
                        .doesNotContain("shopper");
            }
        }
    }

    // --- catalog ------------------------------------------------------------

    @Test
    void searchProductsAnswersTheBriefsNikeQuestion() {
        signIn("aditya");
        var result = catalogTools.searchProducts("t-shirt", "Nike", null, null, null, null);

        assertThat(result.matches()).hasSize(4);
        assertThat(result.matches()).allSatisfy(m -> assertThat(m.brand()).isEqualTo("Nike"));
        assertThat(result.totalMatching()).isEqualTo(4);
    }

    @Test
    void searchResultsNeverCarryAStockCount() {
        signIn("aditya");
        var result = catalogTools.searchProducts(null, "Nike", null, null, null, null);

        // Availability is a band. If a raw count reached the model it would end
        // up quoted to the shopper, and the store does not publish it.
        assertThat(result.matches()).allSatisfy(m ->
                assertThat(m.availability()).isNotNull());
        assertThat(CatalogTools.ProductMatch.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .doesNotContain("stockQuantity", "stock");
    }

    @Test
    void searchIsCappedSoOneCallCannotDrainTheCatalog() {
        signIn("aditya");
        var result = catalogTools.searchProducts(null, null, null, null, null, null);

        assertThat(result.returned()).isLessThanOrEqualTo(8);
        assertThat(result.totalMatching()).isEqualTo(60);
    }

    @Test
    void checkStockAnswersYesOrNoWithoutRevealingTheNumber() {
        signIn("aditya");

        assertThat(catalogTools.checkStock("NIK-TS-001", 5).available()).isTrue();
        assertThat(catalogTools.checkStock("NIK-TS-001", 5000).available()).isFalse();
        assertThat(catalogTools.checkStock("NIK-TS-004", 1).available()).isFalse();

        assertThat(CatalogTools.StockAnswer.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .containsExactly("sku", "requestedQuantity", "available");
    }

    @Test
    void anInventedSkuIsReportedAsUnknownRatherThanUnavailable() {
        signIn("aditya");

        // The failure mode this prevents: the model invents a plausible SKU, the
        // tool says "not available", and the shopper is told a real-sounding
        // product is out of stock.
        assertThatThrownBy(() -> catalogTools.checkStock("NIK-TS-999", 1))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> catalogTools.getProductDetails("MADE-UP-001"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // --- orders -------------------------------------------------------------

    @Test
    void listMyOrdersReturnsOnlyTheSignedInShoppersOrders() {
        signIn("aditya");
        assertThat(orderTools.listMyOrders()).hasSize(5);

        signIn("rahul");
        assertThat(orderTools.listMyOrders()).hasSize(1);

        signIn("demo");
        assertThat(orderTools.listMyOrders()).isEmpty();
    }

    @Test
    void theModelCannotReachAnotherShoppersOrderEvenWithItsRealNumber() {
        signIn("aditya");
        String adityasOrder = orderTools.listMyOrders().getFirst().orderNumber();
        assertThat(orderTools.getOrderStatus(adityasOrder)).isNotNull();

        // Same tool, same real order number, different signed-in shopper.
        signIn("rahul");
        assertThatThrownBy(() -> orderTools.getOrderStatus(adityasOrder))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> orderTools.getDeliveryEstimate(adityasOrder))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deliveryEstimateComesFromRecordedFactsNotGuesswork() {
        signIn("aditya");
        String outForDelivery = orderTools.listMyOrders().stream()
                .filter(o -> o.status() == OrderStatus.OUT_FOR_DELIVERY)
                .findFirst().orElseThrow().orderNumber();

        var estimate = orderTools.getDeliveryEstimate(outForDelivery);

        assertThat(estimate.expectedDeliveryDate()).isNotNull();
        assertThat(estimate.latestUpdate()).isEqualTo("Out for delivery with the local hub");
        assertThat(estimate.latestUpdateAt()).isNotNull();
        assertThat(estimate.deliveredAt()).isNull();
    }

    @Test
    void aCancelledOrderOffersNoDeliveryDateToRepeat() {
        signIn("rahul");
        var estimate = orderTools.getDeliveryEstimate(
                orderTools.listMyOrders().getFirst().orderNumber());

        assertThat(estimate.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(estimate.expectedDeliveryDate()).isNull();
    }

    // --- buying: the two-step split -----------------------------------------

    @Test
    void draftingAPurchaseCreatesNoOrder() {
        signIn("demo");
        assertThat(orderTools.listMyOrders()).isEmpty();

        var draft = purchaseTools.createOrderDraft("NIK-TS-001:2");

        assertThat(draft.draftReference()).isNotBlank();
        assertThat(draft.total()).isEqualByComparingTo("3598");
        assertThat(draft.nextStep()).contains("Nothing has been bought yet");

        // The decisive assertion: after drafting, the shopper still has no orders.
        assertThat(orderTools.listMyOrders()).isEmpty();
    }

    @Test
    void onlyConfirmationTurnsADraftIntoAnOrder() {
        signIn("demo");
        var draft = purchaseTools.createOrderDraft("NIK-TS-001:2, BK-001:1");

        var placed = purchaseTools.confirmOrder(draft.draftReference());

        assertThat(placed.orderNumber()).startsWith("ORD-");
        assertThat(placed.status()).isEqualTo(OrderStatus.PLACED);
        assertThat(placed.total()).isEqualByComparingTo("4197");
        assertThat(orderTools.listMyOrders()).hasSize(1);
    }

    @Test
    void aFabricatedConfirmationReferenceBuysNothing() {
        signIn("demo");

        // A model that skips createOrderDraft and invents a reference cannot
        // reach an order this way.
        assertThatThrownBy(() -> purchaseTools.confirmOrder("not-a-real-draft"))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThat(orderTools.listMyOrders()).isEmpty();
    }

    @Test
    void aShopperCannotConfirmAnotherShoppersDraft() {
        signIn("demo");
        var draft = purchaseTools.createOrderDraft("BK-001:1");

        signIn("rahul");
        assertThatThrownBy(() -> purchaseTools.confirmOrder(draft.draftReference()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void confirmingTwiceDoesNotChargeTwice() {
        signIn("demo");
        var draft = purchaseTools.createOrderDraft("BK-001:1");

        var first = purchaseTools.confirmOrder(draft.draftReference());
        var second = purchaseTools.confirmOrder(draft.draftReference());

        // A model that repeats a tool call must not create a second order.
        assertThat(second.orderNumber()).isEqualTo(first.orderNumber());
        assertThat(orderTools.listMyOrders()).hasSize(1);
    }

    @Test
    void confirmingDecrementsStock() {
        signIn("demo");
        assertThat(catalogTools.checkStock("APL-LP-001", 4).available()).isTrue();

        purchaseTools.confirmOrder(
                purchaseTools.createOrderDraft("APL-LP-001:4").draftReference());

        // Four were in stock and four were bought.
        assertThat(catalogTools.checkStock("APL-LP-001", 1).available()).isFalse();
    }

    @Test
    void anOutOfStockItemCannotEvenBeDrafted() {
        signIn("demo");

        assertThatThrownBy(() -> purchaseTools.createOrderDraft("NIK-TS-004:1"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("not available");
    }

    @Test
    void anExpiredDraftIsRefusedRatherThanChargedAtAStalePrice() {
        signIn("demo");
        var draft = purchaseTools.createOrderDraft("BK-001:1");

        expire(draft.draftReference());

        assertThatThrownBy(() -> purchaseTools.confirmOrder(draft.draftReference()))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("expired");
        assertThat(orderTools.listMyOrders()).isEmpty();
    }

    @Test
    void quantitiesAreBoundedSoOneCallCannotOrderAThousandUnits() {
        signIn("demo");

        assertThatThrownBy(() -> purchaseTools.createOrderDraft("BK-001:500"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("At most");
        assertThatThrownBy(() -> purchaseTools.createOrderDraft("BK-001:0"))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> purchaseTools.createOrderDraft("BK-001:-3"))
                .isInstanceOf(InvalidRequestException.class);
    }

    // --- parsing the model's arguments --------------------------------------

    @Test
    void parsesWellFormedItemStrings() {
        assertThat(PurchaseTools.parseItems("NIK-TS-001:2"))
                .containsExactly(java.util.Map.entry("NIK-TS-001", 2));
        assertThat(PurchaseTools.parseItems(" NIK-TS-001 : 2 , BK-001 : 1 "))
                .containsExactly(java.util.Map.entry("NIK-TS-001", 2),
                        java.util.Map.entry("BK-001", 1));
    }

    @Test
    void mergesRepeatedSkusRatherThanSilentlyDroppingOne() {
        // "one of these, and another two" must buy three, not two.
        assertThat(PurchaseTools.parseItems("BK-001:1, BK-001:2"))
                .containsExactly(java.util.Map.entry("BK-001", 3));
    }

    @Test
    void rejectsMalformedItemStringsInsteadOfGuessing() {
        assertThatThrownBy(() -> PurchaseTools.parseItems("NIK-TS-001"))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> PurchaseTools.parseItems("NIK-TS-001:two"))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> PurchaseTools.parseItems(":3"))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> PurchaseTools.parseItems(""))
                .isInstanceOf(InvalidRequestException.class);
    }

    // --- cancelling ---------------------------------------------------------

    @Test
    void cancelsAnOrderThatHasNotShipped() {
        signIn("aditya");
        String placed = orderTools.listMyOrders().stream()
                .filter(o -> o.status() == OrderStatus.PLACED)
                .findFirst().orElseThrow().orderNumber();

        var cancelled = purchaseTools.cancelOrder(placed);

        assertThat(cancelled.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(orderTools.getDeliveryEstimate(placed).expectedDeliveryDate()).isNull();
    }

    @Test
    void refusesToCancelADeliveredOrder() {
        signIn("aditya");
        String delivered = orderTools.listMyOrders().stream()
                .filter(o -> o.status() == OrderStatus.DELIVERED)
                .findFirst().orElseThrow().orderNumber();

        assertThatThrownBy(() -> purchaseTools.cancelOrder(delivered))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("no longer be cancelled");
    }

    @Test
    void aShopperCannotCancelAnotherShoppersOrder() {
        signIn("aditya");
        String adityasOrder = orderTools.listMyOrders().stream()
                .filter(o -> o.status() == OrderStatus.PLACED)
                .findFirst().orElseThrow().orderNumber();

        signIn("rahul");
        assertThatThrownBy(() -> purchaseTools.cancelOrder(adityasOrder))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // --- helpers ------------------------------------------------------------

    /** Puts a shopper in the security context, as the JWT filter would. */
    private void signIn(String username) {
        AppUser user = userRepository.findByUsername(username).orElseThrow();
        var principal = AppUserPrincipal.of(user, "test-token", Instant.now().plusSeconds(3600));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null,
                        List.of(new SimpleGrantedAuthority(principal.authority()))));
    }

    /** Ages a draft past its expiry, to test the stale-quote path. */
    private void expire(String draftRef) {
        Long userId = ((AppUserPrincipal) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal()).userId();
        var draft = draftRepository.findByPublicRefAndUserId(draftRef, userId).orElseThrow();
        draft.setExpiresAt(Instant.now().minusSeconds(60));
        draftRepository.save(draft);
    }
}
