package de.homelabs.app.mfe;

import java.net.URI;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The micro frontends this app knows: one service base URL per MFE id
 * ({@code app.mfes.<id>.base-url}). The list is the operator's — a service can describe
 * its MFE, but it can never add itself. A bad entry stops the start.
 */
@ConfigurationProperties("app")
public record MfeRegistrations(Map<String, Registration> mfes) {

    /** Shape of an id: it is a URL segment, a custom-element suffix and a role suffix. */
    static final Pattern ID = Pattern.compile("[a-z][a-z0-9]*(-[a-z0-9]+)*");

    /** Path segments the app itself owns; no MFE may take one of them as its id. */
    static final Set<String> RESERVED = Set.of(
            "bff", "api", "mfe", "shell", "oauth2", "login", "logout", "health", "error", "design");

    public MfeRegistrations {
        mfes = mfes == null ? Map.of() : Map.copyOf(new TreeMap<>(mfes));
        mfes.forEach((id, registration) -> {
            if (!ID.matcher(id).matches() || RESERVED.contains(id)) {
                throw new IllegalArgumentException("app.mfes: '" + id + "' is not a valid micro frontend id");
            }
            if (registration == null || registration.baseUrl() == null) {
                throw new IllegalArgumentException("app.mfes." + id + ".base-url is missing");
            }
        });
    }

    public boolean contains(String id) {
        return id != null && mfes.containsKey(id);
    }

    /** One service: where its descriptor, bundles and API live. */
    public record Registration(URI baseUrl) {

        public Registration {
            if (baseUrl != null) {
                String scheme = baseUrl.getScheme();
                if (!("http".equals(scheme) || "https".equals(scheme)) || baseUrl.getHost() == null
                        || baseUrl.getQuery() != null || baseUrl.getFragment() != null) {
                    throw new IllegalArgumentException("base-url must be an absolute http(s) URL: " + baseUrl);
                }
                String text = baseUrl.toString();
                baseUrl = URI.create(text.endsWith("/") ? text.substring(0, text.length() - 1) : text);
            }
        }

        /** The service URL of a path below the base, e.g. {@code /mfe/descriptor}. */
        public URI resolve(String path) {
            return URI.create(baseUrl + path);
        }
    }
}
