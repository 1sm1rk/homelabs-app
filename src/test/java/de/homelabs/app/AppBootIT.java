package de.homelabs.app;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import de.homelabs.app.testsupport.StubClientRegistrationRepository;
import de.homelabs.app.testsupport.TestDatabase;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boot smoke test against a real Postgres: the context starts, Flyway creates the
 * session schema, and the gate answers as designed — open probe, login redirect for
 * page navigations, a plain 401 for script calls, the hardened session cookie.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(StubClientRegistrationRepository.class)
class AppBootIT extends TestDatabase {

    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void servesTheOpenHealthProbe() throws Exception {
        HttpResponse<String> response = get("/health");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"status\":\"UP\"", "homelabs-app");
    }

    @Test
    void redirectsAPageNavigationWithoutSessionToTheLogin() throws Exception {
        for (String path : new String[] { "/", "/projects/HP-1", "/design" }) {
            HttpResponse<String> response = get(path);

            assertThat(response.statusCode()).as("status of %s", path).isEqualTo(302);
            assertThat(response.headers().firstValue("Location")).get().asString()
                    .as("redirect of %s", path)
                    .contains("/oauth2/authorization/" + StubClientRegistrationRepository.REGISTRATION_ID);
        }
    }

    @Test
    void answersScriptCallsWithoutSessionWith401InsteadOfARedirect() throws Exception {
        for (String path : new String[] { "/bff/me", "/bff/mfes", "/api/projects/projects", "/mfe/projects/projects.js" }) {
            HttpResponse<String> response = get(path);

            assertThat(response.statusCode()).as("status of %s", path).isEqualTo(401);
            assertThat(response.headers().firstValue("Location")).as("redirect of %s", path).isEmpty();
        }
    }

    @Test
    void storesTheSessionInPostgresAndHardensItsCookie() throws Exception {
        HttpResponse<String> response = get("/");

        String cookie = response.headers().allValues("Set-Cookie").stream()
                .filter(value -> value.startsWith("APPSESSION="))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no session cookie in " + response.headers().allValues("Set-Cookie")));
        assertThat(cookie).contains("HttpOnly", "SameSite=Lax", "Secure", "Path=/");

        Integer rows = jdbc.queryForObject("select count(*) from spring_session", Integer.class);
        assertThat(rows).as("the remembered request lives in a database session").isPositive();
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
