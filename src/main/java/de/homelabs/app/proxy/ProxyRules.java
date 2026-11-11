package de.homelabs.app.proxy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * What crosses the proxy and what does not — kept free of servlet and HTTP-client types
 * so every rule is a plain unit test. Both directions are allow-lists: a header nobody
 * named here never travels, so neither the session cookie, the CSRF token nor a
 * browser-made Authorization header can reach a backend, and a backend can never set a
 * cookie on the app's origin.
 */
final class ProxyRules {

    /** Request headers a backend gets from the browser; the Bearer token is added separately. */
    static final List<String> REQUEST_HEADERS = List.of(
            "Content-Type", "Accept", "Accept-Language", "If-None-Match", "If-Modified-Since");

    /** Response headers the browser gets from a backend. */
    static final List<String> RESPONSE_HEADERS = List.of(
            "Content-Type", "Content-Disposition", "ETag", "Last-Modified", "Location");

    /** Upper bound for a request or response body (attachments are download-only for now). */
    static final long MAX_BODY_BYTES = 10L * 1024 * 1024;

    private static final Set<String> ENCODED_SEPARATORS = Set.of("%2f", "%5c", "%2e", "%00");

    private ProxyRules() {
    }

    /**
     * The backend path below {@code /mfe} or {@code /api} for a raw (still encoded)
     * request path below the MFE's prefix — empty when the path tries to leave it:
     * dot segments, empty segments, backslashes or encoded separators.
     */
    static Optional<String> backendPath(String rawRest) {
        if (rawRest.isEmpty()) {
            return Optional.of("");
        }
        if (!rawRest.startsWith("/") || rawRest.indexOf('\\') >= 0) {
            return Optional.empty();
        }
        String lower = rawRest.toLowerCase(Locale.ROOT);
        if (ENCODED_SEPARATORS.stream().anyMatch(lower::contains)) {
            return Optional.empty();
        }
        String[] segments = rawRest.substring(1).split("/", -1);
        for (int i = 0; i < segments.length; i++) {
            String segment = segments[i];
            boolean last = i == segments.length - 1;
            if (segment.equals(".") || segment.equals("..") || (segment.isEmpty() && !last)) {
                return Optional.empty();
            }
        }
        return Optional.of(rawRest);
    }

    /** The allowed request headers, looked up by name (multi-valued headers keep every value). */
    static Map<String, List<String>> requestHeaders(Function<String, List<String>> lookup) {
        return pick(REQUEST_HEADERS, lookup);
    }

    /** The allowed response headers, looked up by name. */
    static Map<String, List<String>> responseHeaders(Function<String, List<String>> lookup) {
        return pick(RESPONSE_HEADERS, lookup);
    }

    /**
     * A backend redirect, re-addressed to the app: {@code <base>/api/x} and {@code /api/x}
     * become {@code /api/<id>/x} (same for {@code /mfe}); any other target stays as it is.
     */
    static String location(String location, String baseUrl, String id) {
        String path = location.startsWith(baseUrl + "/") ? location.substring(baseUrl.length()) : location;
        for (String area : List.of("/api", "/mfe")) {
            if (path.equals(area) || path.startsWith(area + "/") || path.startsWith(area + "?")) {
                return area + "/" + id + path.substring(area.length());
            }
        }
        return location;
    }

    private static Map<String, List<String>> pick(List<String> names, Function<String, List<String>> lookup) {
        Map<String, List<String>> picked = new LinkedHashMap<>();
        for (String name : names) {
            List<String> values = lookup.apply(name);
            if (values != null && !values.isEmpty()) {
                picked.put(name, List.copyOf(values));
            }
        }
        return picked;
    }
}
