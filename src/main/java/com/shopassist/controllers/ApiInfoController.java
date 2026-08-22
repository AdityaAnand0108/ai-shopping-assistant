package com.shopassist.controllers;

import java.time.Instant;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Minimal liveness/identity endpoint used to verify the scaffold boots.
 * Deliberately exposes nothing about the schema or internals.
 */
@RestController
@RequestMapping("/api")
public class ApiInfoController {

    @Value("${spring.application.name:shop-assistant}")
    private String applicationName;

    @GetMapping("/info")
    public Map<String, Object> info() {
        return Map.of(
                "application", applicationName,
                "status", "UP",
                "time", Instant.now().toString()
        );
    }
}
