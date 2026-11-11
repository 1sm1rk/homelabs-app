package de.homelabs.app.testsupport;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;

import de.homelabs.app.auth.SecurityConfiguration;

/**
 * Test-only replacement for the auto-configured client registration: the real one reads
 * the live realm's issuer metadata at startup, which no test may depend on. Same id, grant
 * and scopes as {@code application.yml}; the endpoints are deliberately unroutable.
 */
@TestConfiguration(proxyBeanMethods = false)
public class StubClientRegistrationRepository {

    public static final String REGISTRATION_ID = SecurityConfiguration.REGISTRATION_ID;

    @Bean
    ClientRegistrationRepository clientRegistrationRepository() {
        return new InMemoryClientRegistrationRepository(ClientRegistration
                .withRegistrationId(REGISTRATION_ID)
                .clientId("app")
                .clientSecret("test-only-secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope("openid", "profile", "email")
                .issuerUri("http://localhost:0/realms/homelabs")
                .authorizationUri("http://localhost:0/realms/homelabs/protocol/openid-connect/auth")
                .tokenUri("http://localhost:0/realms/homelabs/protocol/openid-connect/token")
                .jwkSetUri("http://localhost:0/realms/homelabs/protocol/openid-connect/certs")
                .userInfoUri("http://localhost:0/realms/homelabs/protocol/openid-connect/userinfo")
                .userNameAttributeName("sub")
                .clientName("homelabs")
                .build());
    }
}
