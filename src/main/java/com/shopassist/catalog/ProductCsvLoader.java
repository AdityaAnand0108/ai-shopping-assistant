package com.shopassist.catalog;

import com.opencsv.CSVReaderHeaderAware;
import com.opencsv.exceptions.CsvValidationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Reads the catalog CSV into {@link Product} instances.
 *
 * <p>Image URLs are derived from the SKU rather than stored in the CSV: they are
 * a presentation detail, not catalog data, and deriving them keeps the data file
 * readable.
 */
@Component
@Slf4j
public class ProductCsvLoader {

    private static final String DEFAULT_CURRENCY = "INR";
    private static final String IMAGE_URL_TEMPLATE = "https://picsum.photos/seed/%s/500/500";

    private final ResourceLoader resourceLoader;

    public ProductCsvLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    public List<Product> load(String location) {
        List<Product> products = new ArrayList<>();
        try (Reader reader = new InputStreamReader(
                resourceLoader.getResource(location).getInputStream(), StandardCharsets.UTF_8);
             CSVReaderHeaderAware csv = new CSVReaderHeaderAware(reader)) {

            Map<String, String> row;
            while ((row = csv.readMap()) != null) {
                products.add(toProduct(row));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to read product CSV at " + location, e);
        } catch (CsvValidationException e) {
            throw new IllegalStateException("Malformed product CSV at " + location, e);
        }

        log.info("Parsed {} products from {}", products.size(), location);
        return products;
    }

    private Product toProduct(Map<String, String> row) {
        String sku = required(row, "sku");
        return Product.builder()
                .sku(sku)
                .name(required(row, "name"))
                .brand(required(row, "brand"))
                .category(required(row, "category"))
                .subcategory(trimToNull(row.get("subcategory")))
                .color(trimToNull(row.get("color")))
                .sizeLabel(trimToNull(row.get("size_label")))
                .material(trimToNull(row.get("material")))
                .description(trimToNull(row.get("description")))
                .price(new BigDecimal(required(row, "price")))
                .currency(DEFAULT_CURRENCY)
                .stockQuantity(Integer.parseInt(required(row, "stock_quantity")))
                .rating(parseNullableDecimal(row.get("rating")))
                .imageUrl(IMAGE_URL_TEMPLATE.formatted(sku))
                .build();
    }

    private static String required(Map<String, String> row, String column) {
        String value = trimToNull(row.get(column));
        if (value == null) {
            throw new IllegalStateException("Product CSV row is missing required column: " + column);
        }
        return value;
    }

    private static BigDecimal parseNullableDecimal(String value) {
        String trimmed = trimToNull(value);
        return trimmed == null ? null : new BigDecimal(trimmed);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
