package de.homelabs.app;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Liveness probe for the container healthcheck; open, carries no data. */
@RestController
class HealthController {

    @GetMapping("/health")
    Map<String, String> health() {
        return Map.of("status", "UP", "service", "homelabs-app");
    }
}
