package de.homelabs.app.proxy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.sun.net.httpserver.HttpExchange;

import jakarta.servlet.http.Cookie;
import com.sun.net.httpserver.HttpServer;

import de.homelabs.app.testsupport.Logins;
import de.homelabs.app.testsupport.StubClientRegistrationRepository;
import de.homelabs.app.testsupport.TestDatabase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The registry, the stage-one gate and the proxy against a fake service (a JDK HTTP
 * server that records what reaches it). Registrations: {@code demo} (well described),
 * {@code broken} (describes another id), {@code roles} (brings its own roles),
 * {@code hidden} (fine, but nobody here holds its role), {@code down} (refuses).
 */
@SpringBootTest(properties = "app.proxy.timeout=1s")
@Import(StubClientRegistrationRepository.class)
class ProxyIT extends TestDatabase {

    private static final HttpServer SERVICE;

    private static final AtomicReference<Seen> SEEN = new AtomicReference<>();

    static {
        try {
            SERVICE = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        SERVICE.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        SERVICE.createContext("/", ProxyIT::serve);
        SERVICE.start();
    }

    private static String base(String name) {
        return "http://127.0.0.1:" + SERVICE.getAddress().getPort() + "/" + name;
    }

    @DynamicPropertySource
    static void registrations(DynamicPropertyRegistry registry) {
        for (String name : List.of("demo", "broken", "roles", "hidden")) {
            registry.add("app.mfes." + name + ".base-url", () -> base(name));
        }
        registry.add("app.mfes.down.base-url", () -> "http://127.0.0.1:9");
    }

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ClientRegistrationRepository clientRegistrations;

    @Autowired
    private OAuth2AuthorizedClientRepository authorizedClients;

    private MockMvc mockMvc;

    private final OAuth2AuthenticationToken person = Logins.login("sub-1", "di.mueller",
            "mfe-demo", "mfe-broken", "mfe-roles", "mfe-down", "mfe-projects", "mfe-unregistered");

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        SEEN.set(null);
    }

    // ---------- /bff/mfes ------------------------------------------------------------------

    @Test
    void offersOnlyGrantedMicroFrontendsWhoseServiceDescribesThemCorrectly() throws Exception {
        mockMvc.perform(get("/bff/mfes").with(authentication(person)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value("demo"))
                .andExpect(jsonPath("$[0].element").value("hl-demo"))
                .andExpect(jsonPath("$[0].entry").value("/mfe/demo/demo.js"))
                .andExpect(jsonPath("$[0].nav.label.de").value("Demo DE"))
                .andExpect(jsonPath("$[0].nav.label.en").value("Demo EN"))
                .andExpect(jsonPath("$[0].nav.icon").value("list"))
                .andExpect(jsonPath("$[0].nav.order").value(20))
                .andExpect(jsonPath("$[0].roles").doesNotExist());
    }

    @Test
    void offersNothingToAPersonWithoutRoles() throws Exception {
        mockMvc.perform(get("/bff/mfes").with(authentication(Logins.login("sub-2", "guest"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ---------- stage-one gate -----------------------------------------------------------

    @Test
    void deniesMicroFrontendsThePersonHoldsNoRoleFor() throws Exception {
        for (String path : List.of("/mfe/hidden/hidden.js", "/api/hidden/items", "/mfe/nope/x.js", "/api/nope/x",
                "/mfe/unregistered/x.js", "/api/unregistered/x")) {
            mockMvc.perform(get(path).with(authentication(person))).andExpect(status().isForbidden());
        }
        assertThat(SEEN.get()).as("the service was never asked").isNull();
    }

    @Test
    void answers401WithoutASession() throws Exception {
        for (String path : List.of("/bff/mfes", "/mfe/demo/demo.js", "/api/demo/items")) {
            mockMvc.perform(get(path)).andExpect(status().isUnauthorized()).andExpect(header().doesNotExist("Location"));
        }
        assertThat(SEEN.get()).isNull();
    }

    // ---------- /mfe proxy -----------------------------------------------------------------

    @Test
    void servesBundlesWithoutTokenOrCookiesAndRevalidating() throws Exception {
        mockMvc.perform(get("/mfe/demo/demo.js").with(authentication(person))
                        .header("Accept", "text/javascript")
                        .header("Cookie", "APPSESSION=secret-session")
                        .header("Authorization", "Bearer forged"))
                .andExpect(status().isOk())
                .andExpect(content().string("customElements.define('hl-demo', class extends HTMLElement {});"))
                .andExpect(header().string("Content-Type", containsString("text/javascript")))
                .andExpect(header().string("ETag", "\"v1\""))
                .andExpect(header().string("Cache-Control", "no-cache"))
                .andExpect(noServiceCookie())
                .andExpect(header().doesNotExist("X-Internal"))
                .andExpect(header().string("Content-Security-Policy", containsString("script-src 'self'")));

        Seen seen = SEEN.get();
        assertThat(seen.path()).isEqualTo("/demo/mfe/demo.js");
        assertThat(seen.headers()).containsEntry("accept", "text/javascript")
                .doesNotContainKeys("authorization", "cookie", "x-xsrf-token");
    }

    @Test
    void passesNotModifiedThrough() throws Exception {
        mockMvc.perform(get("/mfe/demo/demo.js").with(authentication(person)).header("If-None-Match", "\"v1\""))
                .andExpect(status().isNotModified());
    }

    // ---------- /api proxy -----------------------------------------------------------------

    @Test
    void callsTheApiWithTheSessionsAccessTokenOnly() throws Exception {
        MockHttpSession session = sessionWith(token(Instant.now().plusSeconds(300)), null);

        mockMvc.perform(get(URI.create("/api/demo/items?q=a%20b&x=1")).session(session).with(authentication(person))
                        .header("Cookie", "APPSESSION=secret-session")
                        .header("Accept-Language", "de"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"ok\":true}"))
                .andExpect(noServiceCookie());

        Seen seen = SEEN.get();
        assertThat(seen.method()).isEqualTo("GET");
        assertThat(seen.path()).isEqualTo("/demo/api/items");
        assertThat(seen.query()).isEqualTo("q=a%20b&x=1");
        assertThat(seen.headers()).containsEntry("authorization", "Bearer access-1")
                .containsEntry("accept-language", "de")
                .doesNotContainKeys("cookie", "x-xsrf-token");
    }

    @Test
    void refusesWritesWithoutTheCsrfHeader() throws Exception {
        mockMvc.perform(post("/api/demo/items").session(sessionWith(token(Instant.now().plusSeconds(300)), null))
                        .with(authentication(person)).contentType("application/json").content("{\"title\":\"x\"}"))
                .andExpect(status().isForbidden());
        assertThat(SEEN.get()).isNull();
    }

    @Test
    void forwardsWritesWithBodyAndReaddressesTheLocation() throws Exception {
        mockMvc.perform(post("/api/demo/created").session(sessionWith(token(Instant.now().plusSeconds(300)), null))
                        .with(authentication(person)).with(csrfLikeTheShell())
                        .contentType("application/json").content("{\"title\":\"Größe\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/demo/items/7"));

        Seen seen = SEEN.get();
        assertThat(seen.method()).isEqualTo("POST");
        assertThat(seen.body()).isEqualTo("{\"title\":\"Größe\"}");
        assertThat(seen.headers()).containsEntry("content-type", "application/json")
                .doesNotContainKey("x-xsrf-token");
    }

    @Test
    void turnsServiceFailuresAndTimeoutsIntoBadGateway() throws Exception {
        for (String path : List.of("/api/demo/boom", "/api/demo/slow")) {
            mockMvc.perform(get(path).session(sessionWith(token(Instant.now().plusSeconds(300)), null))
                            .with(authentication(person)))
                    .andExpect(status().isBadGateway())
                    .andExpect(header().string("Content-Type", containsString("application/problem+json")))
                    .andExpect(jsonPath("$.status").value(502));
        }
    }

    @Test
    void endsTheSessionWhenTheTokenCannotBeRefreshed() throws Exception {
        MockHttpSession session = sessionWith(token(Instant.now().minusSeconds(300)), new OAuth2RefreshToken(
                "refresh-1", Instant.now().minusSeconds(600)));

        mockMvc.perform(get("/api/demo/items").session(session).with(authentication(person)))
                .andExpect(status().isUnauthorized());

        assertThat(session.isInvalid()).isTrue();
        assertThat(SEEN.get()).isNull();
    }

    @Test
    void refusesPathsThatTryToLeaveTheMicroFrontend() throws Exception {
        MockHttpSession session = sessionWith(token(Instant.now().plusSeconds(300)), null);
        for (String path : List.of("/api/demo/%2e%2e/admin", "/api/demo/a%2fb", "/mfe/demo/..%2f..%2fapi/items",
                "/api/demo//items")) {
            int status = mockMvc.perform(get(URI.create(path)).session(session).with(authentication(person)))
                    .andReturn().getResponse().getStatus();
            assertThat(status).as(path).isBetween(400, 499);
        }
        assertThat(SEEN.get()).isNull();
    }

    // ---------- helpers ------------------------------------------------------------------

    /** What the shell's api wrapper does: the XSRF-TOKEN cookie's value, raw, in the header. */
    private static RequestPostProcessor csrfLikeTheShell() {
        return request -> {
            request.setCookies(new Cookie("XSRF-TOKEN", "shell-token"));
            request.addHeader("X-XSRF-TOKEN", "shell-token");
            return request;
        };
    }

    /** The app may set its own XSRF-TOKEN cookie; a cookie of the service never reaches the browser. */
    private static ResultMatcher noServiceCookie() {
        return result -> assertThat(result.getResponse().getHeaders("Set-Cookie"))
                .allSatisfy(cookie -> assertThat(cookie).startsWith("XSRF-TOKEN="));
    }

    private static OAuth2AccessToken token(Instant expiresAt) {
        return new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "access-1",
                expiresAt.minusSeconds(600), expiresAt);
    }

    private MockHttpSession sessionWith(OAuth2AccessToken accessToken, OAuth2RefreshToken refreshToken) {
        MockHttpSession session = new MockHttpSession();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(session);
        OAuth2AuthorizedClient client = new OAuth2AuthorizedClient(
                clientRegistrations.findByRegistrationId(StubClientRegistrationRepository.REGISTRATION_ID),
                person.getName(), accessToken, refreshToken);
        authorizedClients.saveAuthorizedClient(client, person, request, new MockHttpServletResponse());
        return session;
    }

    record Seen(String method, String path, String query, Map<String, String> headers, String body) {
    }

    private static void serve(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getRawPath();
        Map<String, String> headers = new java.util.TreeMap<>();
        exchange.getRequestHeaders().forEach((name, values) -> headers.put(name.toLowerCase(), String.join(",", values)));
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        if (!path.endsWith("/mfe/descriptor")) {
            SEEN.set(new Seen(exchange.getRequestMethod(), path, exchange.getRequestURI().getRawQuery(), headers, body));
        }
        exchange.getResponseHeaders().add("Set-Cookie", "JSESSIONID=from-the-service");
        exchange.getResponseHeaders().add("X-Internal", "secret");
        switch (path) {
            case "/demo/mfe/descriptor" -> json(exchange, 200, descriptor("demo", null));
            case "/broken/mfe/descriptor" -> json(exchange, 200, descriptor("other", null));
            case "/roles/mfe/descriptor" -> json(exchange, 200, descriptor("roles", "[\"mfe-admin\"]"));
            case "/hidden/mfe/descriptor" -> json(exchange, 200, descriptor("hidden", null));
            case "/demo/mfe/demo.js" -> {
                exchange.getResponseHeaders().add("ETag", "\"v1\"");
                if ("\"v1\"".equals(headers.get("if-none-match"))) {
                    exchange.sendResponseHeaders(304, -1);
                    exchange.close();
                } else {
                    send(exchange, 200, "text/javascript",
                            "customElements.define('hl-demo', class extends HTMLElement {});");
                }
            }
            case "/demo/api/items" -> json(exchange, 200, "{\"ok\":true}");
            case "/demo/api/created" -> {
                exchange.getResponseHeaders().add("Location", base("demo") + "/api/items/7");
                json(exchange, 201, "{\"id\":7}");
            }
            case "/demo/api/boom" -> json(exchange, 500, "{\"error\":\"internal detail\"}");
            case "/demo/api/slow" -> {
                try {
                    Thread.sleep(2500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                json(exchange, 200, "{}");
            }
            default -> json(exchange, 404, "{}");
        }
    }

    private static String descriptor(String id, String roles) {
        return "{\"id\":\"" + id + "\",\"version\":\"1.0.0\",\"element\":\"hl-" + id + "\",\"entry\":\"" + id
                + ".js\",\"nav\":{\"label\":{\"de\":\"Demo DE\",\"en\":\"Demo EN\"},\"icon\":\"list\",\"order\":20}"
                + (roles == null ? "" : ",\"roles\":" + roles) + "}";
    }

    private static void json(HttpExchange exchange, int status, String body) throws IOException {
        send(exchange, status, "application/json", body);
    }

    private static void send(HttpExchange exchange, int status, String type, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", type);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
