package de.homelabs.app.shell;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import de.homelabs.app.testsupport.Logins;
import de.homelabs.app.testsupport.StubClientRegistrationRepository;
import de.homelabs.app.testsupport.TestDatabase;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The single-page fallback, the open shell assets and the CSRF cookie the shell reads. */
@SpringBootTest
@Import(StubClientRegistrationRepository.class)
class ShellIT extends TestDatabase {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void servesTheShellPageForEveryPathTheShellOwns() throws Exception {
        for (String path : new String[] { "/", "/projects", "/projects/HP-12", "/design", "/memory-browser/a/b" }) {
            mockMvc.perform(get(path).with(authentication(Logins.login("sub-1", "di.mueller", "mfe-projects"))))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith("text/html"))
                    .andExpect(content().string(containsString("/shell/app.js")))
                    .andExpect(header().string("Cache-Control", "no-cache"));
        }
    }

    @Test
    void neverServesTheShellPageUnderTheReservedSegments() throws Exception {
        for (String path : new String[] { "/bff", "/bff/unknown", "/api", "/mfe", "/shell", "/error", "/Projects" }) {
            mockMvc.perform(get(path).with(authentication(Logins.login("sub-1", "di.mueller"))))
                    .andExpect(content().string(org.hamcrest.Matchers.not(containsString("/shell/app.js"))));
        }
    }

    @Test
    void servesTheShellAssetsWithoutASession() throws Exception {
        mockMvc.perform(get("/shell/app.js")).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/javascript"));
        mockMvc.perform(get("/shell/ui.css")).andExpect(status().isOk());
        mockMvc.perform(get("/shell/icons.svg")).andExpect(status().isOk());
        mockMvc.perform(get("/shell/i18n/en.json")).andExpect(status().isOk());
    }

}
