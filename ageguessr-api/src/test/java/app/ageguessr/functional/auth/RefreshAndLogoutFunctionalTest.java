package app.ageguessr.functional.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.ageguessr.TestcontainersConfiguration;
import app.ageguessr.functional.auth.AuthFlows.LoggedInSession;
import app.ageguessr.shared.email.RecordingEmailSender;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, AuthFunctionalTestConfig.class})
class RefreshAndLogoutFunctionalTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RecordingEmailSender emailSender;

    @Test
    void refreshingRotatesTheTokenAndTheOldOneCanNeverBeUsedAgain() throws Exception {
        String email = "refresh-" + UUID.randomUUID() + "@x.com";
        AuthFlows.registerAndVerify(mockMvc, objectMapper, emailSender, email, "password123");
        LoggedInSession session = AuthFlows.login(mockMvc, objectMapper, email, "password123");
        Cookie originalCookie = session.asRequestCookie();

        MvcResult firstRefresh = mockMvc.perform(post("/api/auth/refresh").cookie(originalCookie))
                .andExpect(status().isOk())
                .andReturn();
        String rotatedCookieHeader = firstRefresh.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(rotatedCookieHeader).isNotEqualTo(session.cookieHeader());
        Cookie rotatedCookie = new LoggedInSession(null, rotatedCookieHeader).asRequestCookie();

        // Reusing the original (now-revoked) cookie must fail...
        mockMvc.perform(post("/api/auth/refresh").cookie(originalCookie)).andExpect(status().isUnauthorized());

        // ...and reuse detection revokes the whole session, so even the freshly
        // rotated cookie from the same lineage is no longer valid.
        mockMvc.perform(post("/api/auth/refresh").cookie(rotatedCookie)).andExpect(status().isUnauthorized());
    }

    @Test
    void refreshingWithNoCookieIsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/auth/refresh")).andExpect(status().isUnauthorized());
    }

    @Test
    void loggingOutRevokesTheRefreshTokenAndClearsTheCookie() throws Exception {
        String email = "logout-" + UUID.randomUUID() + "@x.com";
        AuthFlows.registerAndVerify(mockMvc, objectMapper, emailSender, email, "password123");
        LoggedInSession session = AuthFlows.login(mockMvc, objectMapper, email, "password123");

        MvcResult logout = mockMvc.perform(post("/api/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + session.accessToken())
                        .cookie(session.asRequestCookie()))
                .andExpect(status().isNoContent())
                .andReturn();
        assertThat(logout.getResponse().getHeader(HttpHeaders.SET_COOKIE)).contains("Max-Age=0");

        mockMvc.perform(post("/api/auth/refresh").cookie(session.asRequestCookie()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loggingOutWithNoCookieIsStillNoContentAsLongAsTheBearerIsValid() throws Exception {
        String email = "logout-no-cookie-" + UUID.randomUUID() + "@x.com";
        AuthFlows.registerAndVerify(mockMvc, objectMapper, emailSender, email, "password123");
        LoggedInSession session = AuthFlows.login(mockMvc, objectMapper, email, "password123");

        mockMvc.perform(post("/api/auth/logout").header(HttpHeaders.AUTHORIZATION, "Bearer " + session.accessToken()))
                .andExpect(status().isNoContent());
    }

    @Test
    void loggingOutWithoutABearerTokenIsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/auth/logout")).andExpect(status().isUnauthorized());
    }
}
