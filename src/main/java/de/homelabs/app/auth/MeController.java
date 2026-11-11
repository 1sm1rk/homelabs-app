package de.homelabs.app.auth;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Who is signed in, as the shell needs it: subject, display name, permitted micro frontends. */
@RestController
class MeController {

    @GetMapping("/bff/me")
    Me me(@AuthenticationPrincipal OidcUser user) {
        return new Me(user.getSubject(), displayName(user), MfeRoles.mfeIds(user.getAuthorities()));
    }

    private static String displayName(OidcUser user) {
        if (user.getPreferredUsername() != null) {
            return user.getPreferredUsername();
        }
        return user.getFullName() != null ? user.getFullName() : user.getSubject();
    }

    record Me(
            @JsonProperty("sub") String sub,
            @JsonProperty("name") String name,
            @JsonProperty("mfes") List<String> mfes) {
    }
}
