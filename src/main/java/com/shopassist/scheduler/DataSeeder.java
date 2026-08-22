package com.shopassist.scheduler;

import com.shopassist.config.SeedProperties;
import com.shopassist.services.DemoDataInstaller;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Installs the demo dataset once at startup, when seeding is enabled.
 *
 * <p>The work itself lives in {@link DemoDataInstaller}. Keeping the startup
 * trigger separate from the logic matters for tests: a runner fires while the
 * context is building, outside any test-managed transaction, so anything it
 * writes is committed for good and leaks into every later test sharing the same
 * in-memory database. Tests therefore leave seeding disabled and call the
 * installer themselves, inside a transaction that rolls back.
 */
@Component
@Order(10)
@Slf4j
public class DataSeeder implements ApplicationRunner {

    private final SeedProperties properties;
    private final DemoDataInstaller installer;

    public DataSeeder(SeedProperties properties, DemoDataInstaller installer) {
        this.properties = properties;
        this.installer = installer;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.enabled()) {
            log.info("Demo data seeding is disabled");
            return;
        }
        installer.install();
    }
}
