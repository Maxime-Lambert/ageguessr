package app.ageguessr.functional.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.ageguessr.TestcontainersConfiguration;
import app.ageguessr.shared.email.RecordingEmailSender;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, AuthFunctionalTestConfig.class})
class LoginFunctionalTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RecordingEmailSender emailSender;

    @Test
    void loggingInWithVerifiedCredentialsReturnsAnAccessTokenAndSetsTheRefreshCookie() throws Exception {
        String email = registerAndVerify();

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(email, "password123")))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).contains("accessToken");

        String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).isNotNull();
        assertThat(setCookie).contains("ageguessr_refresh_token=");
        assertThat(setCookie).containsIgnoringCase("HttpOnly");
        assertThat(setCookie).contains("Path=/api/auth");
        assertThat(setCookie).containsIgnoringCase("SameSite=Lax");
    }

    @Test
    void nonExistentWrongPasswordAndLockedAccountsAllReturnTheExactSameResponse() throws Exception {
        String verifiedEmail = registerAndVerify();
        // Lock the account by exhausting the configured attempt threshold (5, see
        // src/test/resources/application.properties).
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(loginBody(verifiedEmail, "wrong-password")));
        }

        MvcResult notFound = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("nobody-" + UUID.randomUUID() + "@x.com", "whatever")))
                .andExpect(status().isUnauthorized())
                .andReturn();

        MvcResult wrongPassword = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(registerAndVerify(), "wrong-password")))
                .andExpect(status().isUnauthorized())
                .andReturn();

        MvcResult locked = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        // Correct password, but the account is now locked.
                        .content(loginBody(verifiedEmail, "password123")))
                .andExpect(status().isUnauthorized())
                .andReturn();

        String notFoundBody = notFound.getResponse().getContentAsString();
        String wrongPasswordBody = wrongPassword.getResponse().getContentAsString();
        String lockedBody = locked.getResponse().getContentAsString();

        assertThat(notFoundBody).isEqualTo(wrongPasswordBody).isEqualTo(lockedBody);
    }

    @Test
    void anUnverifiedAccountGetsADistinctForbiddenErrorCode() throws Exception {
        String email = "unverified-" + UUID.randomUUID() + "@x.com";
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerBody(email, "password123")));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(email, "password123")))
                .andExpect(status().isForbidden())
                .andExpect(result ->
                        assertThat(result.getResponse().getContentAsString()).contains("EMAIL_NOT_VERIFIED"));
    }

    private String registerAndVerify() throws Exception {
        String email = "login-" + UUID.randomUUID() + "@x.com";
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerBody(email, "password123")));

        String token = VerificationLinks.extractToken(emailSender.lastMessageTo(email));
        mockMvc.perform(post("/api/auth/confirm-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TokenPayload(token))))
                .andExpect(status().isNoContent());
        return email;
    }

    private String registerBody(String email, String password) {
        return objectMapper.writeValueAsString(new CredentialsPayload(email, password));
    }

    private String loginBody(String email, String password) {
        return objectMapper.writeValueAsString(new CredentialsPayload(email, password));
    }

    private record CredentialsPayload(String email, String password) {}

    private record TokenPayload(String token) {}
}
