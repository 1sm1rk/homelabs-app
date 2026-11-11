package de.homelabs.app.mfe;

import java.util.Optional;
import java.util.regex.Pattern;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * What a service says about its micro frontend at {@code GET /mfe/descriptor}. The app
 * trusts none of it blindly: {@link #problem(String)} names the first rule a descriptor
 * breaks, and a descriptor with a problem is not offered at all.
 */
public record Descriptor(
        @JsonProperty("id") String id,
        @JsonProperty("version") String version,
        @JsonProperty("element") String element,
        @JsonProperty("entry") String entry,
        @JsonProperty("nav") Nav nav,
        @JsonProperty("roles") Object roles) {

    /** A relative module path: plain segments only — no scheme, no leading slash, no dot segments. */
    private static final Pattern ENTRY = Pattern.compile("[A-Za-z0-9_-][A-Za-z0-9._-]*(/[A-Za-z0-9_-][A-Za-z0-9._-]*)*");

    private static final Pattern ICON = Pattern.compile("[a-z][a-z0-9-]*");

    /** Shown for an icon name that is no name; unknown sprite names fall back in the shell. */
    static final String GENERIC_ICON = "app";

    static final int DEFAULT_ORDER = 100;

    public record Nav(
            @JsonProperty("label") Label label,
            @JsonProperty("icon") String icon,
            @JsonProperty("order") Integer order) {
    }

    public record Label(@JsonProperty("de") String de, @JsonProperty("en") String en) {
    }

    /** The first rule this descriptor breaks for the registration {@code id}, if any. */
    Optional<String> problem(String registeredId) {
        if (!registeredId.equals(id)) {
            return Optional.of("id '" + id + "' does not match the registration '" + registeredId + "'");
        }
        if (!("hl-" + registeredId).equals(element)) {
            return Optional.of("element must be 'hl-" + registeredId + "', not '" + element + "'");
        }
        if (entry == null || !ENTRY.matcher(entry).matches() || entry.contains("..")) {
            return Optional.of("entry must be a relative module path, not '" + entry + "'");
        }
        if (nav == null || nav.label() == null || isBlank(nav.label().de()) || isBlank(nav.label().en())) {
            return Optional.of("nav.label needs a German and an English text");
        }
        if (roles != null) {
            return Optional.of("a descriptor carries no roles — access is the app's decision");
        }
        return Optional.empty();
    }

    /** The menu entry as the shell gets it: icon name checked, order defaulted. */
    Nav shellNav() {
        String icon = nav.icon() != null && ICON.matcher(nav.icon()).matches() ? nav.icon() : GENERIC_ICON;
        int order = nav.order() != null ? nav.order() : DEFAULT_ORDER;
        return new Nav(new Label(nav.label().de().strip(), nav.label().en().strip()), icon, order);
    }

    private static boolean isBlank(String text) {
        return text == null || text.isBlank();
    }
}
