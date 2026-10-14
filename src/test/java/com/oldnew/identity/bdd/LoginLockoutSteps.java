package com.oldnew.identity.bdd;

import com.oldnew.identity.application.RegisterUser;
import com.oldnew.identity.application.RegisterUserCommand;
import com.oldnew.identity.domain.model.EmailAddress;
import com.oldnew.identity.domain.model.UserAccount;
import com.oldnew.identity.domain.port.UserAccountRepository;
import com.oldnew.identity.support.MockMvcFactory;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;

public class LoginLockoutSteps {

    @Autowired
    WebApplicationContext context;
    @Autowired
    MongoTemplate mongo;
    @Autowired
    RegisterUser registerUser;
    @Autowired
    UserAccountRepository users;

    private MockMvc mvc;
    private MockHttpServletResponse lastRefusal;
    private MockHttpServletResponse wrongPasswordRefusal;

    @Before
    public void cleanDatabase() {
        mongo.remove(new Query(), "users");
        mvc = MockMvcFactory.withSecurity(context);
    }

    @Given("a registered user {string} with password {string}")
    public void aRegisteredUser(String email, String password) {
        registerUser.handle(new RegisterUserCommand(email, password));
    }

    @When("someone signs in as {string} with password {string} {int} times")
    public void signsInRepeatedly(String email, String password, int times) throws Exception {
        for (int i = 0; i < times; i++) {
            wrongPasswordRefusal = signIn(email, password);
            assertThat(wrongPasswordRefusal.getRedirectedUrl()).isEqualTo("/login?error");
        }
    }

    @When("signing in as {string} with password {string} succeeds")
    public void signInSucceeds(String email, String password) throws Exception {
        assertThat(signIn(email, password).getRedirectedUrl()).isNotEqualTo("/login?error");
    }

    @When("signing in as {string} with password {string} is refused")
    public void signInRefused(String email, String password) throws Exception {
        lastRefusal = signIn(email, password);
        assertThat(lastRefusal.getRedirectedUrl()).isEqualTo("/login?error");
    }

    @Then("the account {string} is locked for {int} minutes")
    public void accountIsLocked(String email, int minutes) {
        UserAccount account = users.findByEmail(EmailAddress.of(email)).orElseThrow();
        assertThat(account.isLockedAt(Instant.now())).isTrue();
        assertThat(account.lockedUntil()).isCloseTo(Instant.now().plus(Duration.ofMinutes(minutes)), within(30, ChronoUnit.SECONDS));
    }

    @Then("the account {string} has no failed sign-ins")
    public void noFailedSignIns(String email) {
        assertThat(users.findByEmail(EmailAddress.of(email)).orElseThrow().failedLoginAttempts()).isZero();
    }

    @Then("the refusal looks exactly like a wrong password")
    public void refusalLooksTheSame() throws Exception {
        MockHttpServletResponse wrongPassword = signIn("alice@example.com", "wrong password!");
        assertThat(lastRefusal.getStatus()).isEqualTo(wrongPassword.getStatus());
        assertThat(lastRefusal.getRedirectedUrl()).isEqualTo(wrongPassword.getRedirectedUrl());
        assertThat(lastRefusal.getContentAsString()).isEqualTo(wrongPassword.getContentAsString());
    }

    private MockHttpServletResponse signIn(String email, String password) throws Exception {
        return mvc.perform(formLogin("/login").user(email).password(password)).andReturn().getResponse();
    }
}
