package de.homelabs.app.mfe;

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import de.homelabs.app.mfe.Descriptor.Label;
import de.homelabs.app.mfe.Descriptor.Nav;
import de.homelabs.app.mfe.MfeRegistrations.Registration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class DescriptorTest {

    private static final Nav NAV = new Nav(new Label("Projekte", "Projects"), "folder", 10);

    private static Descriptor descriptor(String id, String element, String entry, Nav nav, Object roles) {
        return new Descriptor(id, "0.3.0", element, entry, nav, roles);
    }

    @Test
    void acceptsAWellFormedDescriptor() {
        assertThat(descriptor("projects", "hl-projects", "projects.js", NAV, null).problem("projects")).isEmpty();
        assertThat(descriptor("projects", "hl-projects", "lib/v2/main.js", NAV, null).problem("projects")).isEmpty();
    }

    @Test
    void rejectsADescriptorThatClaimsAnotherIdentity() {
        assertThat(descriptor("other", "hl-other", "x.js", NAV, null).problem("projects")).isPresent();
        assertThat(descriptor("projects", "hl-other", "x.js", NAV, null).problem("projects")).isPresent();
        assertThat(descriptor("projects", "projects-app", "x.js", NAV, null).problem("projects")).isPresent();
    }

    @Test
    void rejectsEntriesThatLeaveTheServicesMfePath() {
        for (String entry : new String[] { null, "", "/projects.js", "../x.js", "a/../../x.js", "./x.js",
                "https://evil.example/x.js", "//evil.example/x.js", "a//b.js", "a\\b.js", "x.js?y", "a/..x/b.js" }) {
            assertThat(descriptor("projects", "hl-projects", entry, NAV, null).problem("projects")).as(entry).isPresent();
        }
    }

    @Test
    void needsBothMenuLabels() {
        assertThat(descriptor("projects", "hl-projects", "x.js", null, null).problem("projects")).isPresent();
        assertThat(descriptor("projects", "hl-projects", "x.js", new Nav(new Label("Projekte", " "), "folder", 1), null)
                .problem("projects")).isPresent();
    }

    @Test
    void rejectsADescriptorThatBringsItsOwnRoles() {
        assertThat(descriptor("projects", "hl-projects", "x.js", NAV, List.of("mfe-admin")).problem("projects"))
                .isPresent();
    }

    @Test
    void fallsBackToTheGenericIconAndTheDefaultOrder() {
        Nav nav = descriptor("projects", "hl-projects", "x.js",
                new Nav(new Label(" Projekte ", "Projects"), "<svg onload=x>", null), null).shellNav();

        assertThat(nav.icon()).isEqualTo(Descriptor.GENERIC_ICON);
        assertThat(nav.order()).isEqualTo(Descriptor.DEFAULT_ORDER);
        assertThat(nav.label().de()).isEqualTo("Projekte");
    }

    @Test
    void refusesRegistrationsUnderReservedOrMalformedIds() {
        Registration registration = new Registration(URI.create("http://localhost:8093"));
        for (String id : new String[] { "design", "bff", "api", "mfe", "shell", "Projects", "1st", "a_b", "a--b" }) {
            assertThatIllegalArgumentException().as(id).isThrownBy(() -> new MfeRegistrations(Map.of(id, registration)));
        }
        assertThat(new MfeRegistrations(Map.of("memory-browser", registration)).contains("memory-browser")).isTrue();
    }

    @Test
    void normalisesAndChecksTheBaseUrl() {
        assertThat(new Registration(URI.create("http://projects:8093/")).resolve("/mfe/descriptor"))
                .isEqualTo(URI.create("http://projects:8093/mfe/descriptor"));
        assertThatIllegalArgumentException().isThrownBy(() -> new Registration(URI.create("file:///etc")));
        assertThatIllegalArgumentException().isThrownBy(() -> new Registration(URI.create("/relative")));
    }
}
