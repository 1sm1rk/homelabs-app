package de.homelabs.app.testsupport;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;

/** A completed OIDC login as the security chain stores it after the code exchange. */
public final class Logins {

    private Logins() {
    }

    public static OAuth2AuthenticationToken login(String sub, String username, String... mfeRoles) {
        OidcIdToken idToken = new OidcIdToken("id-token", Instant.now(), Instant.now().plusSeconds(300),
                Map.of("sub", sub, "preferred_username", username, "mfe_roles", List.of(mfeRoles)));
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new OidcUserAuthority(idToken));
        for (String role : mfeRoles) {
            authorities.add(new SimpleGrantedAuthority(role));
        }
        DefaultOidcUser user = new DefaultOidcUser(authorities, idToken, "sub");
        return new OAuth2AuthenticationToken(user, authorities, StubClientRegistrationRepository.REGISTRATION_ID);
    }
}
