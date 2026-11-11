package de.homelabs.app.auth;

import java.util.LinkedHashMap;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizedClientManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.authentication.DelegatingAuthenticationEntryPoint;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import de.homelabs.app.mfe.MfeGate;
import de.homelabs.app.mfe.MfeRegistry;

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

    static final String LOGIN_ERROR_PAGE = "/shell/login-error.html";

    /**
     * Same origin for everything; no inline scripts or styles anywhere (constructed
     * stylesheets are not affected). The login form posts to the realm.
     */
    static final String CONTENT_SECURITY_POLICY = "default-src 'self'; script-src 'self'; style-src 'self'; "
            + "img-src 'self' data:; connect-src 'self'; frame-ancestors 'none'; base-uri 'self'; "
            + "form-action 'self' https://auth.homelabs.de";

    @Bean
    SecurityFilterChain appSecurityFilterChain(HttpSecurity http, MfeRegistry registry) throws Exception {
        PathPatternRequestMatcher.Builder paths = PathPatternRequestMatcher.withDefaults();
        MfeGate gate = new MfeGate(registry);
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/health").permitAll()
                        // Shell scripts, styles, icons and chrome texts: static, no data.
                        .requestMatchers(HttpMethod.GET, "/shell/**").permitAll()
                        // Stage one: the person holds the MFE's role (unknown id → denied).
                        .requestMatchers(paths.matcher("/mfe/{id}/**"), paths.matcher("/api/{id}/**")).access(gate)
                        .anyRequest().authenticated())
                // A failed sign-in (e.g. two racing in parallel tabs) lands on a static
                // page with one way back, not on a non-existent /login.
                .oauth2Login(login -> login.failureUrl(LOGIN_ERROR_PAGE))
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(entryPoint()))
                .csrf(csrf -> csrf.spa())
                .logout(logout -> logout
                        .logoutRequestMatcher(PathPatternRequestMatcher.withDefaults()
                                .matcher(HttpMethod.POST, "/bff/logout"))
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT))
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("APPSESSION"))
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(CONTENT_SECURITY_POLICY))
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.SAME_ORIGIN)));
        return http.build();
    }

    /**
     * Hands the proxy a valid access token: the stored one, or a refreshed one when it is
     * about to expire. A failed refresh is the end of the sign-in.
     */
    @Bean
    OAuth2AuthorizedClientManager authorizedClientManager(ClientRegistrationRepository registrations,
            OAuth2AuthorizedClientRepository authorizedClients) {
        DefaultOAuth2AuthorizedClientManager manager =
                new DefaultOAuth2AuthorizedClientManager(registrations, authorizedClients);
        manager.setAuthorizedClientProvider(OAuth2AuthorizedClientProviderBuilder.builder()
                .authorizationCode()
                .refreshToken()
                .build());
        return manager;
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
