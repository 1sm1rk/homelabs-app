package de.homelabs.app.auth;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;

import de.homelabs.app.AppApplication;
import de.homelabs.app.testsupport.Logins;
import de.homelabs.app.testsupport.StubClientRegistrationRepository;
import de.homelabs.app.testsupport.TestDatabase;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The point of the JDBC session store: a signed-in session written by one application
 * instance is still signed in after that instance is gone and a new one serves the
 * request — what a deploy does.
 */
class SessionSurvivesRestartIT extends TestDatabase {

    @Test
    @SuppressWarnings("unchecked")
    void aSessionOfTheOldInstanceIsStillSignedInOnTheNewOne() throws Exception {
        String sessionId;
        try (ConfigurableApplicationContext first = start()) {
            SessionRepository<Session> sessions = first.getBean(SessionRepository.class);
            Session session = sessions.createSession();
            session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                    new SecurityContextImpl(Logins.login("sub-restart", "di.mueller", "mfe-projects")));
            sessions.save(session);
            sessionId = session.getId();
        }

        try (ConfigurableApplicationContext second = start()) {
            int port = ((WebServerApplicationContext) second).getWebServer().getPort();
            String cookie = Base64.getEncoder().encodeToString(sessionId.getBytes(StandardCharsets.UTF_8));

            HttpResponse<String> me = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.NEVER)
                    .build()
                    .send(HttpRequest.newBuilder()
                            .uri(URI.create("http://localhost:" + port + "/bff/me"))
                            .header("Cookie", "APPSESSION=" + cookie)
                            .timeout(Duration.ofSeconds(10))
                            .GET()
                            .build(), HttpResponse.BodyHandlers.ofString());

            assertThat(me.statusCode()).isEqualTo(200);
            assertThat(me.body()).contains("\"sub\":\"sub-restart\"", "\"projects\"");
        }
    }

    private static ConfigurableApplicationContext start() {
        // Command-line arguments: they outrank application.yml (builder defaults would not).
        Map<String, String> properties = new HashMap<>(TestDatabase.properties());
        properties.put("server.port", "0");
        String[] args = properties.entrySet().stream()
                .map(entry -> "--" + entry.getKey() + "=" + entry.getValue())
                .toArray(String[]::new);
        return new SpringApplicationBuilder(AppApplication.class, StubClientRegistrationRepository.class)
                .run(args);
    }
}
