package de.homelabs.app.proxy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProxyRulesTest {

    @Test
    void forwardsPlainPathsUnchanged() {
        assertThat(ProxyRules.backendPath("")).contains("");
        assertThat(ProxyRules.backendPath("/")).contains("/");
        assertThat(ProxyRules.backendPath("/projects/HP-1")).contains("/projects/HP-1");
        assertThat(ProxyRules.backendPath("/projects/")).contains("/projects/");
        assertThat(ProxyRules.backendPath("/files/a%20b.pdf")).contains("/files/a%20b.pdf");
        assertThat(ProxyRules.backendPath("/lib/v1.2/x.js")).contains("/lib/v1.2/x.js");
    }

    @Test
    void refusesEveryWayOutOfTheMicroFrontendsPrefix() {
        for (String path : List.of("/..", "/../admin", "/a/../../b", "/./a", "/a/.", "//evil", "/a//b",
                "/%2e%2e/admin", "/%2E%2e/admin", "/a%2fb", "/a%2Fb", "/a%5cb", "/a\\b", "/a%00", "relative")) {
            assertThat(ProxyRules.backendPath(path)).as(path).isEmpty();
        }
    }

    @Test
    void passesOnlyTheAllowedRequestHeaders() {
        Map<String, List<String>> browser = Map.of(
                "Content-Type", List.of("application/json"),
                "Accept", List.of("application/json"),
                "Accept-Language", List.of("de"),
                "If-None-Match", List.of("\"v1\"", "\"v2\""),
                "Cookie", List.of("APPSESSION=abc"),
                "Authorization", List.of("Bearer from-the-browser"),
                "X-XSRF-TOKEN", List.of("token"),
                "Connection", List.of("keep-alive"),
                "Host", List.of("app.homelabs.de"));

        assertThat(ProxyRules.requestHeaders(browser::get)).containsOnlyKeys(
                "Content-Type", "Accept", "Accept-Language", "If-None-Match")
                .containsEntry("If-None-Match", List.of("\"v1\"", "\"v2\""));
    }

    @Test
    void passesOnlyTheAllowedResponseHeaders() {
        Map<String, List<String>> backend = Map.of(
                "Content-Type", List.of("text/javascript"),
                "ETag", List.of("\"v1\""),
                "Set-Cookie", List.of("JSESSIONID=x"),
                "Access-Control-Allow-Origin", List.of("*"),
                "Transfer-Encoding", List.of("chunked"));

        assertThat(ProxyRules.responseHeaders(backend::get)).containsOnlyKeys("Content-Type", "ETag");
    }

    @Test
    void readdressesBackendRedirectsToTheApp() {
        String base = "http://projects:8093";
        assertThat(ProxyRules.location(base + "/api/projects/7", base, "projects")).isEqualTo("/api/projects/projects/7");
        assertThat(ProxyRules.location("/api/projects/7", base, "projects")).isEqualTo("/api/projects/projects/7");
        assertThat(ProxyRules.location("/api?x=1", base, "projects")).isEqualTo("/api/projects?x=1");
        assertThat(ProxyRules.location("/mfe/projects.js", base, "projects")).isEqualTo("/mfe/projects/projects.js");
        assertThat(ProxyRules.location("https://elsewhere.example/x", base, "projects"))
                .isEqualTo("https://elsewhere.example/x");
        assertThat(ProxyRules.location("/apis/x", base, "projects")).isEqualTo("/apis/x");
    }
}
