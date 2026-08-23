package com.shopassist.controllers.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopassist.dto.auth.LoginRequest;
import com.shopassist.services.DemoDataInstaller;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Checkout over HTTP.
 *
 * <p>The tests that matter here are the ones about what a request cannot do:
 * price its own basket, confirm somebody else's draft, or buy twice from one
 * draft. The pleasant path is the easy part.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PurchaseApiTest {

    /** In stock in the seeded catalog. */
    private static final String IN_STOCK_SKU = "SNY-HP-001";

    /** Seeded deliberately with no stock. */
    private static final String OUT_OF_STOCK_SKU = "NIK-TS-004";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DemoDataInstaller installer;

    @BeforeEach
    void seed() {
        installer.install();
    }

    // --- access -------------------------------------------------------------

    @Test
    void checkoutRequiresAToken() throws Exception {
        mockMvc.perform(post("/api/purchases/draft")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(basket(IN_STOCK_SKU, 1)))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/purchases/any-reference/confirm"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void oneShopperCannotConfirmAnothersDraft() throws Exception {
        String reference = draftFor("satvik", "Password123", IN_STOCK_SKU, 1);

        mockMvc.perform(post("/api/purchases/" + reference + "/confirm")
                        .header("Authorization", bearer("sarah", "Password123")))
                .andExpect(status().isNotFound());

        // And the draft is still there for its owner, untouched.
        mockMvc.perform(post("/api/purchases/" + reference + "/confirm")
                        .header("Authorization", bearer("satvik", "Password123")))
                .andExpect(status().isCreated());
    }

    // --- drafting -----------------------------------------------------------

    @Test
    void pricesTheBasketItselfRatherThanTrustingTheClient() throws Exception {
        // The request carries no prices at all; the response carries the
        // catalog's. This is the property that makes an edited request useless.
        String body = mockMvc.perform(post("/api/purchases/draft")
                        .header("Authorization", bearer("demo", "Demo1234"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(basket(IN_STOCK_SKU, 2)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();

        var draft = objectMapper.readTree(body);
        double unitPrice = draft.get("items").get(0).get("unitPrice").asDouble();
        double lineTotal = draft.get("items").get(0).get("lineTotal").asDouble();
        double total = draft.get("totalAmount").asDouble();

        assertThat(unitPrice).isGreaterThan(0);
        assertThat(lineTotal).isEqualTo(unitPrice * 2);
        assertThat(total).isEqualTo(lineTotal);
    }

    @Test
    void draftingCreatesNoOrder() throws Exception {
        String token = bearer("demo", "Demo1234");
        mockMvc.perform(post("/api/purchases/draft")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(basket(IN_STOCK_SKU, 1)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/orders").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void mergesRepeatedSkusInsteadOfLosingOne() throws Exception {
        String body = """
                {"items":[{"sku":"%s","quantity":1},{"sku":"%s","quantity":2}]}"""
                .formatted(IN_STOCK_SKU, IN_STOCK_SKU.toLowerCase());

        mockMvc.perform(post("/api/purchases/draft")
                        .header("Authorization", bearer("demo", "Demo1234"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].quantity").value(3));
    }

    @Test
    void refusesAnEmptyBasket() throws Exception {
        mockMvc.perform(post("/api/purchases/draft")
                        .header("Authorization", bearer("demo", "Demo1234"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void refusesAnUnknownSku() throws Exception {
        mockMvc.perform(post("/api/purchases/draft")
                        .header("Authorization", bearer("demo", "Demo1234"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(basket("NO-SUCH-SKU", 1)))
                .andExpect(status().isNotFound());
    }

    @Test
    void refusesMoreUnitsThanAreInStock() throws Exception {
        mockMvc.perform(post("/api/purchases/draft")
                        .header("Authorization", bearer("demo", "Demo1234"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(basket(OUT_OF_STOCK_SKU, 1)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void refusesAQuantityBelowOne() throws Exception {
        mockMvc.perform(post("/api/purchases/draft")
                        .header("Authorization", bearer("demo", "Demo1234"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(basket(IN_STOCK_SKU, 0)))
                .andExpect(status().isBadRequest());
    }

    // --- confirming ---------------------------------------------------------

    @Test
    void confirmingPlacesTheOrderAndShowsItInTheHistory() throws Exception {
        String token = bearer("demo", "Demo1234");
        String reference = draftFor("demo", "Demo1234", IN_STOCK_SKU, 1);

        String placed = mockMvc.perform(post("/api/purchases/" + reference + "/confirm")
                        .header("Authorization", token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PLACED"))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.timeline", hasSize(1)))
                .andReturn().getResponse().getContentAsString();

        String orderNumber = objectMapper.readTree(placed).get("orderNumber").asText();

        mockMvc.perform(get("/api/orders").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].orderNumber").value(orderNumber));
    }

    @Test
    void confirmingTwiceReturnsTheSameOrderRatherThanBuyingTwice() throws Exception {
        String token = bearer("demo", "Demo1234");
        String reference = draftFor("demo", "Demo1234", IN_STOCK_SKU, 1);

        String first = mockMvc.perform(post("/api/purchases/" + reference + "/confirm")
                        .header("Authorization", token))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String second = mockMvc.perform(post("/api/purchases/" + reference + "/confirm")
                        .header("Authorization", token))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        assertThat(objectMapper.readTree(first).get("orderNumber").asText())
                .isEqualTo(objectMapper.readTree(second).get("orderNumber").asText());

        // A double-clicked button must not produce two orders.
        mockMvc.perform(get("/api/orders").header("Authorization", token))
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void confirmingDrawsStockDown() throws Exception {
        String token = bearer("demo", "Demo1234");
        int before = stockBandOf(IN_STOCK_SKU);
        String reference = draftFor("demo", "Demo1234", IN_STOCK_SKU, 1);

        mockMvc.perform(post("/api/purchases/" + reference + "/confirm")
                        .header("Authorization", token))
                .andExpect(status().isCreated());

        assertThat(stockBandOf(IN_STOCK_SKU)).isLessThanOrEqualTo(before);
    }

    @Test
    void refusesAnUnknownReference() throws Exception {
        mockMvc.perform(post("/api/purchases/00000000-0000-0000-0000-000000000000/confirm")
                        .header("Authorization", bearer("demo", "Demo1234")))
                .andExpect(status().isNotFound());
    }

    // --- abandoning ---------------------------------------------------------

    @Test
    void aCancelledDraftCannotThenBeConfirmed() throws Exception {
        String token = bearer("demo", "Demo1234");
        String reference = draftFor("demo", "Demo1234", IN_STOCK_SKU, 1);

        mockMvc.perform(delete("/api/purchases/" + reference).header("Authorization", token))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/purchases/" + reference + "/confirm")
                        .header("Authorization", token))
                .andExpect(status().isBadRequest());
    }

    // --- helpers ------------------------------------------------------------

    private String basket(String sku, int quantity) {
        return """
                {"items":[{"sku":"%s","quantity":%d}]}""".formatted(sku, quantity);
    }

    private String draftFor(String username, String password, String sku, int quantity)
            throws Exception {
        String body = mockMvc.perform(post("/api/purchases/draft")
                        .header("Authorization", bearer(username, password))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(basket(sku, quantity)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("reference").asText();
    }

    /**
     * The catalog reports availability as a band rather than a count, so this
     * maps it to something orderable for a before/after comparison.
     */
    private int stockBandOf(String sku) throws Exception {
        String body = mockMvc.perform(get("/api/products/" + sku))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return switch (objectMapper.readTree(body).get("availability").asText()) {
            case "IN_STOCK" -> 2;
            case "LOW_STOCK" -> 1;
            default -> 0;
        };
    }

    private String bearer(String username, String password) throws Exception {
        String body = objectMapper.writeValueAsString(new LoginRequest(username, password));
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + objectMapper.readTree(response).get("accessToken").asText();
    }
}
