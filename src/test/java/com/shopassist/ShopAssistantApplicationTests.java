package com.shopassist;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class ShopAssistantApplicationTests {

    @Test
    void contextLoads() {
        // Phase 0 smoke test: the application context must start with no
        // external services running (no Ollama, no MySQL).
    }
}
