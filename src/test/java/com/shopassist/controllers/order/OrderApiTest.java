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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The order API, and the ownership boundary it has to hold.
 *
 * <p>The cross-account tests here are the ones that matter most: they are the
 * behaviour the assistant will inherit unchanged in Phase 5.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class OrderApiTest {

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
    void ordersRequireAToken() throws Exception {
        mockMvc.perform(get("/api/orders"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/orders/ORD-2026-000101"))
                .andExpect(status().isUnauthorized());
    }

    // --- listing ------------------------------------------------------------

    @Test
    void listsTheSignedInShoppersOrdersNewestFirst() throws Exception {
        mockMvc.perform(get("/api/orders").header("Authorization", bearer("aditya", "Password123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(5)))
                .andExpect(jsonPath("$[0].status").value("PLACED"));
    }

    @Test
    void showsAnEmptyListForAShopperWithNoOrders() throws Exception {
        mockMvc.perform(get("/api/orders").header("Authorization", bearer("demo", "Demo1234")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void summaryCountsUnitsRatherThanLines() throws Exception {
        // Priya's delivered order is a single line of three tees.
        String body = mockMvc.perform(get("/api/orders")
                        .header("Authorization", bearer("priya", "Password123")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        var delivered = objectMapper.readTree(body).valueStream()
                .filter(o -> "DELIVERED".equals(o.get("status").asText()))
                .findFirst().orElseThrow();

        assertThat(delivered.get("itemCount").asInt()).isEqualTo(3);
        assertThat(delivered.get("totalAmount").asDouble()).isEqualTo(2970.0);
    }

    // --- the ownership boundary ---------------------------------------------

    @Test
    void aShopperCannotReadAnotherShoppersOrder() throws Exception {
        String adityasOrder = firstOrderNumberOf("aditya", "Password123");

        mockMvc.perform(get("/api/orders/" + adityasOrder)
                        .header("Authorization", bearer("aditya", "Password123")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/orders/" + adityasOrder)
                        .header("Authorization", bearer("rahul", "Password123")))
                .andExpect(status().isNotFound());
    }

    @Test
    void aRealOrderBelongingToSomeoneElseIsIndistinguishableFromOneThatDoesNotExist()
            throws Exception {
        String adityasOrder = firstOrderNumberOf("aditya", "Password123");
        String rahulsToken = bearer("rahul", "Password123");

        String notYours = mockMvc.perform(get("/api/orders/" + adityasOrder)
                        .header("Authorization", rahulsToken))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();

        String neverExisted = mockMvc.perform(get("/api/orders/ORD-2026-999999")
                        .header("Authorization", rahulsToken))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();

        // Only the order number and timestamp may differ; nothing signals that
        // one of these numbers is real.
        assertThat(shape(notYours)).isEqualTo(shape(neverExisted));
    }

    @Test
    void theTimelineIsOwnerScopedToo() throws Exception {
        String adityasOrder = firstOrderNumberOf("aditya", "Password123");

        mockMvc.perform(get("/api/orders/" + adityasOrder + "/timeline")
                        .header("Authorization", bearer("rahul", "Password123")))
                .andExpect(status().isNotFound());
    }

    // --- detail -------------------------------------------------------------

    @Test
    void returnsLinesAndTrackingTimelineTogether() throws Exception {
        String token = bearer("aditya", "Password123");
        String delivered = orderNumberWithStatus("aditya", "Password123", "DELIVERED");

        mockMvc.perform(get("/api/orders/" + delivered).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERED"))
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[*].sku").exists())
                .andExpect(jsonPath("$.timeline", hasSize(6)))
                .andExpect(jsonPath("$.timeline[0].status").value("PLACED"))
                .andExpect(jsonPath("$.timeline[5].status").value("DELIVERED"))
                .andExpect(jsonPath("$.deliveredAt").exists());
    }

    @Test
    void marksAnInFlightOrderCancellableAndADeliveredOneNot() throws Exception {
        String token = bearer("aditya", "Password123");

        mockMvc.perform(get("/api/orders/" + orderNumberWithStatus("aditya", "Password123", "PLACED"))
                        .header("Authorization", token))
                .andExpect(jsonPath("$.cancellable").value(true));

        mockMvc.perform(get("/api/orders/" + orderNumberWithStatus("aditya", "Password123", "DELIVERED"))
                        .header("Authorization", token))
                .andExpect(jsonPath("$.cancellable").value(false));
    }

    @Test
    void aCancelledOrderCarriesNoDeliveryPromise() throws Exception {
        String token = bearer("rahul", "Password123");
        String cancelled = firstOrderNumberOf("rahul", "Password123");

        mockMvc.perform(get("/api/orders/" + cancelled).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.expectedDeliveryDate").doesNotExist())
                .andExpect(jsonPath("$.cancelledAt").exists())
                .andExpect(jsonPath("$.cancellable").value(false));
    }

    @Test
    void orderPayloadsCarryNoInternalIdentifiers() throws Exception {
        String token = bearer("aditya", "Password123");
        String detail = mockMvc.perform(
                        get("/api/orders/" + firstOrderNumberOf("aditya", "Password123"))
                                .header("Authorization", token))
                .andReturn().getResponse().getContentAsString();

        assertThat(detail)
                .doesNotContain("\"id\"")
                .doesNotContain("userId")
                .doesNotContain("productId")
                .doesNotContain("passwordHash");
    }

    // --- helpers ------------------------------------------------------------

    private String bearer(String username, String password) throws Exception {
        String body = objectMapper.writeValueAsString(
                new com.shopassist.dto.auth.LoginRequest(username, password));
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + objectMapper.readTree(response).get("accessToken").asText();
    }

    private String firstOrderNumberOf(String username, String password) throws Exception {
        String body = mockMvc.perform(get("/api/orders")
                        .header("Authorization", bearer(username, password)))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get(0).get("orderNumber").asText();
    }

    private String orderNumberWithStatus(String username, String password, String status)
            throws Exception {
        String body = mockMvc.perform(get("/api/orders")
                        .header("Authorization", bearer(username, password)))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).valueStream()
                .filter(o -> status.equals(o.get("status").asText()))
                .findFirst().orElseThrow()
                .get("orderNumber").asText();
    }

    /** Strips the fields that legitimately differ, leaving the disclosure surface. */
    private String shape(String problemJson) throws Exception {
        var node = (com.fasterxml.jackson.databind.node.ObjectNode)
                objectMapper.readTree(problemJson);
        node.remove("timestamp");
        node.remove("detail");
        node.remove("instance");
        return node.toString();
    }
}
