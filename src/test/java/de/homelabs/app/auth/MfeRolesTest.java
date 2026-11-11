package de.homelabs.app.auth;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;

import static org.assertj.core.api.Assertions.assertThat;

class MfeRolesTest {

    private final MfeRoles mapper = new MfeRoles();

    @Test
    void turnsTheConventionalRolesOfTheClaimIntoAuthorities() {
        Collection<? extends GrantedAuthority> mapped = mapper.mapAuthorities(List.of(
                authority(Map.of("sub", "s", "mfe_roles", List.of("mfe-projects", "mfe-memory-browser")))));

        assertThat(names(mapped)).contains("mfe-projects", "mfe-memory-browser", "OIDC_USER");
        assertThat(MfeRoles.mfeIds(mapped)).containsExactly("projects", "memory-browser");
    }

    @Test
    void ignoresClaimValuesOutsideTheConvention() {
        Collection<? extends GrantedAuthority> mapped = mapper.mapAuthorities(List.of(
                authority(Map.of("sub", "s", "mfe_roles", List.of("admin", "mfe-", "mfe-Projects", "mfe-a/b", 7)))));

        assertThat(MfeRoles.mfeIds(mapped)).isEmpty();
        assertThat(names(mapped)).containsExactly("OIDC_USER");
    }

    @Test
    void grantsNothingWithoutTheClaim() {
        Collection<? extends GrantedAuthority> mapped = mapper.mapAuthorities(List.of(
                authority(Map.of("sub", "s")), new SimpleGrantedAuthority("SCOPE_openid")));

        assertThat(MfeRoles.mfeIds(mapped)).isEmpty();
        assertThat(names(mapped)).containsExactlyInAnyOrder("OIDC_USER", "SCOPE_openid");
    }

    @Test
    void ignoresAClaimThatIsNotAList() {
        Collection<? extends GrantedAuthority> mapped = mapper.mapAuthorities(List.of(
                authority(Map.of("sub", "s", "mfe_roles", "mfe-projects"))));

        assertThat(MfeRoles.mfeIds(mapped)).isEmpty();
    }

    private static OidcUserAuthority authority(Map<String, Object> claims) {
        return new OidcUserAuthority(new OidcIdToken("t", Instant.now(), Instant.now().plusSeconds(60), claims));
    }

    private static List<String> names(Collection<? extends GrantedAuthority> authorities) {
        return authorities.stream().map(GrantedAuthority::getAuthority).toList();
    }
}
