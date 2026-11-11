package de.homelabs.app.testsupport;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;

import de.homelabs.app.auth.MfeRoles;

/**
 * A completed OIDC login shaped exactly like the real one: the OIDC user carries only the
 * raw login authority (with the role claim in its ID token), and the authentication token
 * carries what the application's authorities mapper made of it.
 */
public final class Logins {

    private Logins() {
    }

    public static OAuth2AuthenticationToken login(String sub, String username, String... mfeRoles) {
        OidcIdToken idToken = new OidcIdToken("id-token", Instant.now(), Instant.now().plusSeconds(300),
                Map.of("sub", sub, "preferred_username", username, "mfe_roles", List.of(mfeRoles)));
        List<GrantedAuthority> raw = List.of(new OidcUserAuthority(idToken));
        DefaultOidcUser user = new DefaultOidcUser(raw, idToken, "sub");
        Collection<? extends GrantedAuthority> mapped = new MfeRoles().mapAuthorities(raw);
        return new OAuth2AuthenticationToken(user, mapped, StubClientRegistrationRepository.REGISTRATION_ID);
    }
}
