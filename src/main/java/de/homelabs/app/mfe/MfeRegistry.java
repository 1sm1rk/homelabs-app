package de.homelabs.app.mfe;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import de.homelabs.app.mfe.Descriptor.Nav;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * The micro frontends a person may open right now: the registrations their roles grant,
 * each with the descriptor its service currently serves. Descriptors are fetched lazily
 * and kept for 30 seconds — a new MFE version shows up without a restart, and a service
 * that is down or describes itself wrongly simply drops out of the menu.
 */
@Component
public class MfeRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(MfeRegistry.class);

    static final Duration CACHE_TIME = Duration.ofSeconds(30);

    private static final Duration FETCH_TIMEOUT = Duration.ofSeconds(3);

    private final MfeRegistrations registrations;

    private final JsonMapper json;

    private final HttpClient http = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(FETCH_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    public MfeRegistry(MfeRegistrations registrations, JsonMapper json) {
        this.registrations = registrations;
        this.json = json;
    }

    /** One micro frontend as the shell mounts it. */
    public record Mfe(String id, String element, String entry, Nav nav) {
    }

    /** The micro frontends these authorities grant whose service describes them correctly, in menu order. */
    public List<Mfe> permitted(Iterable<? extends GrantedAuthority> authorities) {
        return granted(authorities).stream()
                .flatMap(id -> descriptor(id).map(descriptor -> new Mfe(id, descriptor.element(),
                        "/mfe/" + id + "/" + descriptor.entry(), descriptor.shellNav())).stream())
                .sorted(Comparator.comparingInt((Mfe mfe) -> mfe.nav().order()).thenComparing(Mfe::id))
                .toList();
    }

    /** Stage one of authorization: the id is registered and the person holds its role. */
    public boolean grants(String id, Iterable<? extends GrantedAuthority> authorities) {
        return registrations.contains(id) && granted(authorities).contains(id);
    }

    private List<String> granted(Iterable<? extends GrantedAuthority> authorities) {
        List<String> ids = new ArrayList<>();
        for (GrantedAuthority authority : authorities) {
            String name = authority.getAuthority();
            if (name != null && name.startsWith("mfe-") && registrations.contains(name.substring(4))) {
                ids.add(name.substring(4));
            }
        }
        return ids;
    }

    private Optional<Descriptor> descriptor(String id) {
        Instant now = Instant.now();
        return cache.compute(id, (key, cached) -> cached != null && cached.until().isAfter(now)
                ? cached
                : new Cached(fetch(key), now.plus(CACHE_TIME))).descriptor();
    }

    private Optional<Descriptor> fetch(String id) {
        var url = registrations.mfes().get(id).resolve("/mfe/descriptor");
        try {
            HttpResponse<byte[]> response = http.send(
                    HttpRequest.newBuilder(url).timeout(FETCH_TIMEOUT).header("Accept", "application/json").GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                LOG.warn("Micro frontend '{}' left out: {} answered {}", id, url, response.statusCode());
                return Optional.empty();
            }
            Descriptor descriptor = json.readValue(response.body(), Descriptor.class);
            Optional<String> problem = descriptor.problem(id);
            if (problem.isPresent()) {
                LOG.warn("Micro frontend '{}' left out: {}", id, problem.get());
                return Optional.empty();
            }
            return Optional.of(descriptor);
        } catch (IOException | JacksonException e) {
            LOG.warn("Micro frontend '{}' left out: {} not readable ({})", id, url, e.toString());
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }

    private record Cached(Optional<Descriptor> descriptor, Instant until) {
    }
}
