package de.homelabs.app.auth;

import java.util.LinkedHashMap;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.DelegatingAuthenticationEntryPoint;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * The login gate of the backend for frontend.
 *
 * <p>Everything but the liveness probe needs a session. A page navigation without one is
 * sent to the Keycloak login; a call of the shell or a micro frontend ({@code /bff/**},
 * {@code /api/**}, {@code /mfe/**}) gets a plain 401 instead, because a script cannot
 * follow a login redirect — the shell turns the 401 into a full-page sign-in.
 *
 * <p>The tokens of a login are kept in the HTTP session (and with it in Postgres), not in
 * an in-memory service: a session that survives a restart must still be able to call the
 * backends. CSRF follows the single-page-application shape — a cookie the shell reads and
 * sends back as a header on every write.
 *
 * <p>Sign-out ends the app session only; the realm's own SSO session lives on.
 */
@Configuration
public class SecurityConfiguration {

    /** Registration id of the realm client in {@code application.yml}. */
    public static final String REGISTRATION_ID = "keycloak";

    @Bean
    SecurityFilterChain appSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/health").permitAll()
                        // Shell scripts, styles, icons and chrome texts: static, no data.
                        .requestMatchers(HttpMethod.GET, "/shell/**").permitAll()
                        .anyRequest().authenticated())
                .oauth2Login(Customizer.withDefaults())
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(entryPoint()))
                .csrf(csrf -> csrf.spa())
                .logout(logout -> logout
                        .logoutRequestMatcher(PathPatternRequestMatcher.withDefaults()
                                .matcher(HttpMethod.POST, "/bff/logout"))
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT))
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("APPSESSION"));
        return http.build();
    }

    @Bean
    GrantedAuthoritiesMapper mfeRoles() {
        return new MfeRoles();
    }

    @Bean
    OAuth2AuthorizedClientRepository authorizedClientRepository() {
        return new HttpSessionOAuth2AuthorizedClientRepository();
    }

    private static AuthenticationEntryPoint entryPoint() {
        LinkedHashMap<RequestMatcher, AuthenticationEntryPoint> entryPoints = new LinkedHashMap<>();
        entryPoints.put(scriptCalls(), new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED));
        DelegatingAuthenticationEntryPoint delegating = new DelegatingAuthenticationEntryPoint(entryPoints);
        delegating.setDefaultEntryPoint(
                new LoginUrlAuthenticationEntryPoint("/oauth2/authorization/" + REGISTRATION_ID));
        return delegating;
    }

    private static RequestMatcher scriptCalls() {
        PathPatternRequestMatcher.Builder paths = PathPatternRequestMatcher.withDefaults();
        return new OrRequestMatcher(
                paths.matcher("/bff/**"),
                paths.matcher("/api/**"),
                paths.matcher("/mfe/**"));
    }
}
