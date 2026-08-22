package com.shopassist.controllers.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopassist.dto.catalog.ProductSearchCriteria;
import com.shopassist.services.DemoDataInstaller;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The public catalog API. Deliberately exercised without a token, because
 * browsing is meant to work signed out.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ProductApiTest {

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
    void browsingWorksWithoutSigningIn() throws Exception {
        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(60));
    }

    // --- the brief's example question ---------------------------------------

    @Test
    void answersWhatNikeTShirtsAreAvailable() throws Exception {
        mockMvc.perform(get("/api/products")
                        .param("q", "t-shirt").param("brand", "Nike"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(4)))
                .andExpect(jsonPath("$.content[*].brand", everyItem(org.hamcrest.Matchers.is("Nike"))));
    }

    @Test
    void excludesTheOutOfStockTeeWhenOnlyAvailableItemsAreWanted() throws Exception {
        mockMvc.perform(get("/api/products")
                        .param("brand", "Nike").param("q", "t-shirt").param("inStockOnly", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(3)))
                .andExpect(jsonPath("$.content[?(@.sku == 'NIK-TS-004')]", hasSize(0)));
    }

    // --- filtering and paging -----------------------------------------------

    @Test
    void appliesPriceBounds() throws Exception {
        // Asserted against parsed BigDecimals rather than Hamcrest number
        // matchers: JSON numbers arrive as Integer or Double depending on the
        // decimal scale, so a matcher typed to one of them fails on the other.
        String body = mockMvc.perform(get("/api/products")
                        .param("minPrice", "500").param("maxPrice", "1200"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        var root = objectMapper.readTree(body);
        assertThat(root.get("totalElements").asInt()).isPositive();
        assertThat(root.get("content").valueStream().toList())
                .isNotEmpty()
                .allSatisfy(item -> assertThat(item.get("price").decimalValue())
                        .isBetween(new java.math.BigDecimal("500"), new java.math.BigDecimal("1200")));
    }

    @Test
    void rejectsAMinimumPriceAboveTheMaximum() throws Exception {
        mockMvc.perform(get("/api/products")
                        .param("minPrice", "5000").param("maxPrice", "100"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request"))
                .andExpect(jsonPath("$.detail").value("minPrice cannot be greater than maxPrice"));
    }

    @Test
    void capsThePageSizeSoTheCatalogCannotBeDrainedInOneCall() throws Exception {
        mockMvc.perform(get("/api/products").param("size", "10000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(ProductSearchCriteria.MAX_PAGE_SIZE))
                .andExpect(jsonPath("$.content", hasSize(ProductSearchCriteria.MAX_PAGE_SIZE)));
    }

    @Test
    void pagesThroughTheCatalogWithoutRepeatingOrLosingAProduct() throws Exception {
        var seen = new java.util.LinkedHashSet<String>();
        int size = 25;

        for (int page = 0; page < 3; page++) {
            String body = mockMvc.perform(get("/api/products")
                            .param("page", String.valueOf(page)).param("size", String.valueOf(size)))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            objectMapper.readTree(body).get("content")
                    .forEach(item -> seen.add(item.get("sku").asText()));
        }

        // 60 distinct SKUs across three pages proves the tiebreak sort is stable.
        assertThat(seen).hasSize(60);
    }

    @Test
    void sortsByPriceAscending() throws Exception {
        mockMvc.perform(get("/api/products")
                        .param("sort", "PRICE").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].price").value(349.0));
    }

    @Test
    void rejectsASortKeyOutsideTheAllowlist() throws Exception {
        // "stockQuantity" is a real entity field; the allowlist is what stops it
        // being used to order results and infer inventory.
        mockMvc.perform(get("/api/products").param("sort", "stockQuantity"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("The value supplied for 'sort' is not valid."));
    }

    @Test
    void rejectsANonNumericPriceWithoutLeakingTheTargetType() throws Exception {
        String body = mockMvc.perform(get("/api/products").param("minPrice", "cheap"))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("BigDecimal").doesNotContain("java.lang");
    }

    // --- detail -------------------------------------------------------------

    @Test
    void returnsFullDetailForOneSku() throws Exception {
        mockMvc.perform(get("/api/products/NIK-TS-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sku").value("NIK-TS-001"))
                .andExpect(jsonPath("$.name").value("Nike Dri-FIT Legend Training T-Shirt"))
                .andExpect(jsonPath("$.description").exists())
                .andExpect(jsonPath("$.color").value("Black"))
                .andExpect(jsonPath("$.size").value("M"))
                .andExpect(jsonPath("$.availability").value("IN_STOCK"));
    }

    @Test
    void matchesASkuRegardlessOfCase() throws Exception {
        mockMvc.perform(get("/api/products/nik-ts-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sku").value("NIK-TS-001"));
    }

    @Test
    void returnsNotFoundForAnUnknownSku() throws Exception {
        mockMvc.perform(get("/api/products/NOPE-000"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Not found"));
    }

    // --- data minimisation --------------------------------------------------

    @Test
    void reportsAvailabilityAsABandRatherThanTheStockCount() throws Exception {
        // 42 in stock, 4 in stock, 0 in stock: three bands, no numbers.
        mockMvc.perform(get("/api/products/NIK-TS-001"))
                .andExpect(jsonPath("$.availability").value("IN_STOCK"));
        mockMvc.perform(get("/api/products/APL-LP-001"))
                .andExpect(jsonPath("$.availability").value("LOW_STOCK"));
        mockMvc.perform(get("/api/products/NIK-TS-004"))
                .andExpect(jsonPath("$.availability").value("OUT_OF_STOCK"));
    }

    @Test
    void neverExposesInternalColumns() throws Exception {
        String list = mockMvc.perform(get("/api/products").param("size", "50"))
                .andReturn().getResponse().getContentAsString();
        String detail = mockMvc.perform(get("/api/products/NIK-TS-001"))
                .andReturn().getResponse().getContentAsString();

        for (String payload : new String[]{list, detail}) {
            assertThat(payload)
                    .doesNotContain("stockQuantity")
                    .doesNotContain("createdAt")
                    .doesNotContain("updatedAt")
                    .doesNotContain("\"id\"");
        }
    }

    @Test
    void publishesTheFilterValuesTheFrontendNeeds() throws Exception {
        mockMvc.perform(get("/api/products/filters"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categories", hasSize(6)))
                .andExpect(jsonPath("$.brands", hasSize(greaterThan(20))));
    }
}
