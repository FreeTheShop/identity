package com.oldnew.identity.api;

import com.oldnew.identity.support.IntegrationTest;
import com.oldnew.identity.support.MockMvcFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** What a person sees after a failed sign-in: the login form again, with one generic message. */
@IntegrationTest
class LoginPageIT {

    @Autowired
    WebApplicationContext context;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcFactory.withSecurity(context);
    }

    private MockHttpSession failedSignIn(String username) throws Exception {
        return failedSignIn(username, "not the password");
    }

    private MockHttpSession failedSignIn(String username, String password) throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/login").session(session).with(csrf())
                        .param("username", username).param("password", password))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/login?error"));
        return session;
    }

    @Test
    void aFailedSignInShowsTheFormAgainWithAGenericMessage() throws Exception {
        assertGenericErrorPage(failedSignIn("nobody-" + UUID.randomUUID() + "@example.com"));
    }

    @Test
    void aLockedAccountLooksLikeAnyOtherFailure() throws Exception {
        String email = "locked-" + UUID.randomUUID() + "@example.com";
        mvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"correct horse battery\"}"))
                .andExpect(status().isCreated());
        for (int i = 0; i < 5; i++) {
            failedSignIn(email);
        }

        assertGenericErrorPage(failedSignIn(email, "correct horse battery"));
    }

    private void assertGenericErrorPage(MockHttpSession session) throws Exception {
        mvc.perform(get("/login?error").session(session).accept(MediaType.TEXT_HTML))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("Invalid credentials"),
                        containsString("name=\"username\""),
                        containsString("name=\"password\""),
                        // Never says which part was wrong, or that an account exists or is locked.
                        not(containsString("Bad credentials")),
                        not(containsString("locked")))));
    }
}
