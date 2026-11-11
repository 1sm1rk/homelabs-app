package de.homelabs.app.shell;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

import de.homelabs.app.testsupport.StubClientRegistrationRepository;
import de.homelabs.app.testsupport.TestDatabase;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The first answer the shell receives already carries the CSRF cookie its writes send
 * back — the single-page CSRF setup loads the token eagerly instead of deferring it.
 * Over a real socket, because MockMvc's security support swaps the token repository.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(StubClientRegistrationRepository.class)
class CsrfCookieIT extends TestDatabase {

    @LocalServerPort
    private int port;

    @Test
    void theFirstShellAnswerCarriesAScriptReadableCsrfCookie() throws Exception {
        HttpResponse<Void> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/shell/app.js")).build(),
                HttpResponse.BodyHandlers.discarding());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().allValues("Set-Cookie"))
                .anySatisfy(cookie -> assertThat(cookie).startsWith("XSRF-TOKEN=").doesNotContainIgnoringCase("HttpOnly"));
    }
}
