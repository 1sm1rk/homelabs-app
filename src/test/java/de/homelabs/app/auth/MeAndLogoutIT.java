package de.homelabs.app.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import de.homelabs.app.testsupport.Logins;
import de.homelabs.app.testsupport.StubClientRegistrationRepository;
import de.homelabs.app.testsupport.TestDatabase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The signed-in person as the shell sees it, and the end of the session. */
@SpringBootTest
@Import(StubClientRegistrationRepository.class)
class MeAndLogoutIT extends TestDatabase {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private OAuth2AuthorizedClientRepository authorizedClients;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void describesThePersonWithTheMicroFrontendsTheirRolesGrant() throws Exception {
        mockMvc.perform(get("/bff/me").with(authentication(Logins.login("sub-1", "di.mueller", "mfe-projects"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sub").value("sub-1"))
                .andExpect(jsonPath("$.name").value("di.mueller"))
                .andExpect(jsonPath("$.mfes[0]").value("projects"))
                .andExpect(jsonPath("$.mfes.length()").value(1));
    }

    @Test
    void listsNoMicroFrontendForAPersonWithoutRoles() throws Exception {
        mockMvc.perform(get("/bff/me").with(authentication(Logins.login("sub-2", "guest"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mfes.length()").value(0));
    }

    @Test
    void signingOutEndsTheSessionWith204() throws Exception {
        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(post("/bff/logout").session(session)
                        .with(authentication(Logins.login("sub-1", "di.mueller")))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void rejectsASignOutWithoutTheCsrfToken() throws Exception {
        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(post("/bff/logout").session(session)
                        .with(authentication(Logins.login("sub-1", "di.mueller"))))
                .andExpect(status().isForbidden());

        assertThat(session.isInvalid()).isFalse();
    }

    @Test
    void keepsTheTokensOfALoginInTheSession() {
        assertThat(authorizedClients)
                .as("tokens must survive a restart together with the session, so they live in it")
                .isInstanceOf(HttpSessionOAuth2AuthorizedClientRepository.class);
    }
}
