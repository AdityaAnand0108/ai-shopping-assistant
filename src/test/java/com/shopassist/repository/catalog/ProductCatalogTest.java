package com.shopassist.repository.catalog;

import com.shopassist.config.SeedProperties;
import com.shopassist.entity.catalog.Product;
import com.shopassist.util.catalog.ProductCsvLoader;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the Phase 1 catalog end to end: CSV parsing, the Flyway schema and
 * the JPA mapping all have to agree for these to pass.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProductCatalogTest {

    @Autowired
    private ProductCsvLoader csvLoader;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private SeedProperties seedProperties;

    @BeforeEach
    void loadCatalog() {
        productRepository.saveAll(csvLoader.load(seedProperties.productsCsv()));
    }

    @Test
    void loadsEveryRowFromTheCsv() {
        assertThat(productRepository.count()).isEqualTo(60);
    }

    @Test
    void parsesQuotedFieldsContainingCommasAndApostrophes() {
        // "Dell Inspiron 15 Laptop (i5, 16GB, 512GB SSD)" has commas inside quotes,
        // and the Levi's rows carry an apostrophe in both name and brand.
        assertThat(productRepository.findBySkuIgnoreCase("DEL-LP-001"))
                .get()
                .extracting(Product::getName)
                .isEqualTo("Dell Inspiron 15 Laptop (i5, 16GB, 512GB SSD)");

        assertThat(productRepository.findBySkuIgnoreCase("LEV-JN-001"))
                .get()
                .extracting(Product::getBrand)
                .isEqualTo("Levi's");
    }

    @Test
    void mapsEveryColumnForASingleProduct() {
        Product tee = productRepository.findBySkuIgnoreCase("NIK-TS-001").orElseThrow();

        assertThat(tee.getName()).isEqualTo("Nike Dri-FIT Legend Training T-Shirt");
        assertThat(tee.getBrand()).isEqualTo("Nike");
        assertThat(tee.getCategory()).isEqualTo("Apparel");
        assertThat(tee.getSubcategory()).isEqualTo("T-Shirts");
        assertThat(tee.getColor()).isEqualTo("Black");
        assertThat(tee.getSizeLabel()).isEqualTo("M");
        assertThat(tee.getPrice()).isEqualByComparingTo("1799");
        assertThat(tee.getCurrency()).isEqualTo("INR");
        assertThat(tee.getStockQuantity()).isEqualTo(42);
        assertThat(tee.getRating()).isEqualByComparingTo("4.4");
        assertThat(tee.getImageUrl()).contains("NIK-TS-001");
        assertThat(tee.getDescription()).contains("Dri-FIT");
        assertThat(tee.getCreatedAt()).isNotNull();
    }

    @Test
    void leavesBlankCsvCellsAsNullRatherThanEmptyStrings() {
        Product headphones = productRepository.findBySkuIgnoreCase("SNY-HP-001").orElseThrow();
        assertThat(headphones.getSizeLabel()).isNull();
    }

    @Test
    void answersTheBriefsNikeTShirtQuestion() {
        var results = productRepository.search(
                "t-shirt", "Nike", null, null, null, false, PageRequest.of(0, 20));

        assertThat(results.getContent())
                .isNotEmpty()
                .allSatisfy(p -> assertThat(p.getBrand()).isEqualTo("Nike"))
                .extracting(Product::getSku)
                .contains("NIK-TS-001", "NIK-TS-002", "NIK-TS-003", "NIK-TS-004");
    }

    @Test
    void inStockOnlyExcludesTheOutOfStockProduct() {
        Product outOfStock = productRepository.findBySkuIgnoreCase("NIK-TS-004").orElseThrow();
        assertThat(outOfStock.isInStock()).isFalse();

        var available = productRepository.search(
                null, "Nike", null, null, null, true, PageRequest.of(0, 50));

        assertThat(available.getContent())
                .extracting(Product::getSku)
                .doesNotContain("NIK-TS-004");
    }

    @Test
    void appliesPriceBoundsInSql() {
        var budget = productRepository.search(
                null, null, "Apparel", BigDecimal.valueOf(500), BigDecimal.valueOf(1200),
                false, PageRequest.of(0, 50));

        assertThat(budget.getContent())
                .isNotEmpty()
                .allSatisfy(p -> assertThat(p.getPrice())
                        .isBetween(BigDecimal.valueOf(500), BigDecimal.valueOf(1200)));
    }

    @Test
    void exposesDistinctBrandsAndCategoriesForFilters() {
        List<String> brands = productRepository.findDistinctBrands();
        List<String> categories = productRepository.findDistinctCategories();

        assertThat(brands).contains("Nike", "Adidas", "Sony", "Levi's").doesNotHaveDuplicates();
        assertThat(categories)
                .containsExactly("Apparel", "Books", "Electronics", "Footwear", "Home", "Sports");
    }

    @Test
    void buildsEmbeddableTextCarryingTheFieldsSemanticSearchNeeds() {
        Product tee = productRepository.findBySkuIgnoreCase("NIK-TS-001").orElseThrow();
        String text = tee.toEmbeddableText();

        assertThat(text)
                .contains("Nike Dri-FIT Legend Training T-Shirt")
                .contains("Brand: Nike")
                .contains("Category: Apparel / T-Shirts")
                .contains("Colour: Black");
    }
}
