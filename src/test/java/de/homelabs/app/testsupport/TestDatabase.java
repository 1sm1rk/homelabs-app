package de.homelabs.app.testsupport;

import java.util.Map;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * One Postgres for all integration tests of the run (the session store needs a real
 * database; Flyway migrates it on every context start). Started once, stopped by the
 * Testcontainers reaper when the JVM ends.
 */
public abstract class TestDatabase {

    protected static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        properties().forEach((key, value) -> registry.add(key, () -> value));
    }

    /** Datasource, secret and registration properties for a context started outside the test framework. */
    public static Map<String, String> properties() {
        return Map.of(
                "spring.datasource.url", POSTGRES.getJdbcUrl(),
                "spring.datasource.username", POSTGRES.getUsername(),
                "spring.datasource.password", POSTGRES.getPassword(),
                "spring.security.oauth2.client.registration.keycloak.client-secret", "test-only-secret",
                // Hermetic: never the dev service on :8093 (port 9 = discard, refused locally).
                "app.mfes.projects.base-url", "http://127.0.0.1:9");
    }
}
