package com.shopassist.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Controls demo data seeding. Disabled in tests so each test class controls its
 * own fixtures.
 *
 * @param enabled      whether to seed at all
 * @param productsCsv  classpath location of the catalog CSV
 * @param demoPassword password given to every seeded demo account
 */
@ConfigurationProperties(prefix = "shopassist.seed")
public record SeedProperties(
        boolean enabled,
        String productsCsv,
        String demoPassword
) {
    public SeedProperties {
        if (productsCsv == null || productsCsv.isBlank()) {
            productsCsv = "classpath:data/products.csv";
        }
        if (demoPassword == null || demoPassword.isBlank()) {
            demoPassword = "Password123";
        }
    }
}
