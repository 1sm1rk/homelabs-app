package de.homelabs.app.proxy;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.homelabs.app.auth.SecurityConfiguration;
import de.homelabs.app.mfe.MfeRegistrations;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import tools.jackson.databind.json.JsonMapper;

/**
 * The thin same-origin proxy: {@code /mfe/{id}/**} to the service's {@code /mfe/**}
 * (bundles, no token) and {@code /api/{id}/**} to its {@code /api/**} with the session's
 * access token as Bearer. The stage-one gate has already run when a request gets here;
 * {@link ProxyRules} decides what crosses. Bodies stream in both directions.
 */
@RestController
class ProxyController {

    private static final Logger LOG = LoggerFactory.getLogger(ProxyController.class);

    private final MfeRegistrations registrations;

    private final OAuth2AuthorizedClientManager authorizedClients;

    private final JsonMapper json;

    private final Duration timeout;

    private final HttpClient http;

    ProxyController(MfeRegistrations registrations, OAuth2AuthorizedClientManager authorizedClients, JsonMapper json,
            @Value("${app.proxy.timeout:10s}") Duration timeout) {
        this.registrations = registrations;
        this.authorizedClients = authorizedClients;
        this.json = json;
        this.timeout = timeout;
        this.http = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(timeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @RequestMapping("/mfe/{id}/**")
    void bundle(@PathVariable String id, HttpServletRequest request, HttpServletResponse response) throws IOException {
        forward("mfe", id, null, request, response);
    }

    @RequestMapping("/api/{id}/**")
    void api(@PathVariable String id, Authentication authentication, HttpServletRequest request,
            HttpServletResponse response) throws IOException {
        Optional<String> token = accessToken(authentication, request, response);
        if (token.isEmpty()) {
            HttpSession session = request.getSession(false);
            if (session != null) {
                session.invalidate();
            }
            SecurityContextHolder.clearContext();
            problem(response, HttpStatus.UNAUTHORIZED, "The sign-in has expired.");
            return;
        }
        forward("api", id, token.get(), request, response);
    }

    private void forward(String area, String id, String bearer, HttpServletRequest request,
            HttpServletResponse response) throws IOException {
        String prefix = "/" + area + "/" + id;
        String rawPath = request.getRequestURI();
        Optional<String> rest = rawPath.startsWith(prefix) ? ProxyRules.backendPath(rawPath.substring(prefix.length()))
                : Optional.empty();
        if (rest.isEmpty()) {
            problem(response, HttpStatus.BAD_REQUEST, "This path cannot be forwarded.");
            return;
        }
        long length = request.getContentLengthLong();
        if (length > ProxyRules.MAX_BODY_BYTES) {
            problem(response, HttpStatus.CONTENT_TOO_LARGE, "The request body is too large.");
            return;
        }

        MfeRegistrations.Registration registration = registrations.mfes().get(id);
        String query = request.getQueryString();
        URI target = registration.resolve("/" + area + rest.get() + (query == null ? "" : "?" + query));

        HttpRequest.Builder outgoing = HttpRequest.newBuilder(target).timeout(timeout)
                .method(request.getMethod(), body(request, length));
        ProxyRules.requestHeaders(name -> Collections.list(request.getHeaders(name)))
                .forEach((name, values) -> values.forEach(value -> outgoing.header(name, value)));
        if (bearer != null) {
            outgoing.header("Authorization", "Bearer " + bearer);
        }

        HttpResponse<InputStream> answer;
        try {
            answer = http.send(outgoing.build(), HttpResponse.BodyHandlers.ofInputStream());
        } catch (IOException e) {
            LOG.warn("{} {} failed: {}", request.getMethod(), target, e.toString());
            problem(response, HttpStatus.BAD_GATEWAY, "The service did not answer.");
            return;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            problem(response, HttpStatus.BAD_GATEWAY, "The service did not answer.");
            return;
        }

        try (InputStream in = answer.body()) {
            if (answer.statusCode() >= 500) {
                LOG.warn("{} {} answered {}", request.getMethod(), target, answer.statusCode());
                problem(response, HttpStatus.BAD_GATEWAY, "The service failed.");
                return;
            }
            long announced = answer.headers().firstValueAsLong("Content-Length").orElse(-1);
            if (announced > ProxyRules.MAX_BODY_BYTES) {
                problem(response, HttpStatus.BAD_GATEWAY, "The service answer is too large.");
                return;
            }
            response.setStatus(answer.statusCode());
            ProxyRules.responseHeaders(name -> answer.headers().allValues(name)).forEach((name, values) -> values
                    .forEach(value -> response.addHeader(name, "Location".equals(name)
                            ? ProxyRules.location(value, registration.baseUrl().toString(), id)
                            : value)));
            if ("mfe".equals(area)) {
                response.setHeader("Cache-Control", "no-cache");
            }
            if (announced >= 0) {
                response.setContentLengthLong(announced);
            }
            copy(in, response.getOutputStream());
        }
    }

    private static HttpRequest.BodyPublisher body(HttpServletRequest request, long length) {
        if (length == 0 || (length < 0 && request.getHeader("Transfer-Encoding") == null)) {
            return HttpRequest.BodyPublishers.noBody();
        }
        HttpRequest.BodyPublisher stream = HttpRequest.BodyPublishers.ofInputStream(() -> {
            try {
                return new LimitedInputStream(request.getInputStream(), ProxyRules.MAX_BODY_BYTES);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
        return length > 0 ? HttpRequest.BodyPublishers.fromPublisher(stream, length) : stream;
    }

    private Optional<String> accessToken(Authentication authentication, HttpServletRequest request,
            HttpServletResponse response) {
        try {
            OAuth2AuthorizedClient client = authorizedClients.authorize(OAuth2AuthorizeRequest
                    .withClientRegistrationId(SecurityConfiguration.REGISTRATION_ID)
                    .principal(authentication)
                    .attribute(HttpServletRequest.class.getName(), request)
                    .attribute(HttpServletResponse.class.getName(), response)
                    .build());
            return client == null ? Optional.empty() : Optional.of(client.getAccessToken().getTokenValue());
        } catch (OAuth2AuthorizationException e) {
            LOG.info("Token refresh for {} failed: {}", authentication.getName(), e.getError().getErrorCode());
            return Optional.empty();
        }
    }

    private static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[16 * 1024];
        long total = 0;
        int read;
        while ((read = in.read(buffer)) != -1) {
            total += read;
            if (total > ProxyRules.MAX_BODY_BYTES) {
                throw new IOException("Service answer exceeds " + ProxyRules.MAX_BODY_BYTES + " bytes");
            }
            out.write(buffer, 0, read);
        }
        out.flush();
    }

    private void problem(HttpServletResponse response, HttpStatus status, String detail) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "about:blank");
        body.put("title", status.getReasonPhrase());
        body.put("status", status.value());
        body.put("detail", detail);
        response.setStatus(status.value());
        response.setContentType("application/problem+json");
        json.writeValue(response.getOutputStream(), body);
    }

    /** Ends the stream with an error once more than {@code limit} bytes were read. */
    private static final class LimitedInputStream extends InputStream {

        private final InputStream in;

        private long remaining;

        LimitedInputStream(InputStream in, long limit) {
            this.in = in;
            this.remaining = limit;
        }

        @Override
        public int read() throws IOException {
            int b = in.read();
            if (b >= 0 && --remaining < 0) {
                throw new IOException("Request body exceeds the limit");
            }
            return b;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int read = in.read(buffer, offset, length);
            if (read > 0 && (remaining -= read) < 0) {
                throw new IOException("Request body exceeds the limit");
            }
            return read;
        }

        @Override
        public void close() throws IOException {
            in.close();
        }
    }
}
