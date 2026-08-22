package com.shopassist;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Excludes {@link UserDetailsServiceAutoConfiguration} because it does not back
 * off just because a SecurityFilterChain exists: left alone it would create a
 * default in-memory "user" account with a generated password. Authentication
 * here is entirely JWT-based, so that account would be an unused credential with
 * real access.
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
public class ShopAssistantApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShopAssistantApplication.class, args);
    }
}
