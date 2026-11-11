package de.homelabs.app.mfe;

import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.annotation.JsonProperty;

import de.homelabs.app.mfe.Descriptor.Nav;

/** The shell's menu: only the micro frontends the signed-in person may open. */
@RestController
class MfesController {

    private final MfeRegistry registry;

    MfesController(MfeRegistry registry) {
        this.registry = registry;
    }

    @GetMapping("/bff/mfes")
    List<MfeView> mfes(Authentication authentication) {
        return registry.permitted(authentication.getAuthorities()).stream()
                .map(mfe -> new MfeView(mfe.id(), mfe.element(), mfe.entry(), mfe.nav()))
                .toList();
    }

    record MfeView(
            @JsonProperty("id") String id,
            @JsonProperty("element") String element,
            @JsonProperty("entry") String entry,
            @JsonProperty("nav") Nav nav) {
    }
}
