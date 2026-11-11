package de.homelabs.app.auth;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;
import org.springframework.security.oauth2.core.user.OAuth2UserAuthority;

/**
 * Turns the realm's micro-frontend roles into authorities.
 *
 * <p>Keycloak puts the client roles of {@code app} into the claim {@code mfe_roles}. Each
 * value that follows the naming convention {@code mfe-<id>} becomes an authority of the
 * same name; anything else in the claim is ignored, so a mis-assigned role never turns
 * into access. The authorities from the login itself (scopes, the OIDC marker) stay.
 */
public final class MfeRoles implements GrantedAuthoritiesMapper {

    /** Claim the client-role mapper of the realm client writes. */
    public static final String CLAIM = "mfe_roles";

    /** Prefix every micro-frontend role carries; the rest is the micro frontend's id. */
    public static final String PREFIX = "mfe-";

    private static final Pattern ROLE = Pattern.compile("mfe-[a-z0-9]+(-[a-z0-9]+)*");

    @Override
    public Collection<? extends GrantedAuthority> mapAuthorities(Collection<? extends GrantedAuthority> authorities) {
        Set<GrantedAuthority> mapped = new LinkedHashSet<>(authorities);
        for (GrantedAuthority authority : authorities) {
            Map<String, Object> claims = switch (authority) {
                case OidcUserAuthority oidc -> oidc.getIdToken().getClaims();
                case OAuth2UserAuthority user -> user.getAttributes();
                default -> Map.of();
            };
            roles(claims.get(CLAIM)).forEach(role -> mapped.add(new SimpleGrantedAuthority(role)));
        }
        return mapped;
    }

    /** The ids of the micro frontends the given authorities grant, in their order. */
    public static List<String> mfeIds(Collection<? extends GrantedAuthority> authorities) {
        return authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .filter(name -> name != null && ROLE.matcher(name).matches())
                .map(name -> name.substring(PREFIX.length()))
                .distinct()
                .toList();
    }

    private static List<String> roles(Object claim) {
        if (!(claim instanceof Collection<?> values)) {
            return List.of();
        }
        return values.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .filter(value -> ROLE.matcher(value).matches())
                .toList();
    }
}
