package app.ageguessr.functional.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.ageguessr.TestcontainersConfiguration;
import app.ageguessr.features.auth.TermsVersion;
import app.ageguessr.functional.auth.AuthFlows.LoggedInSession;
import app.ageguessr.shared.email.RecordingEmailSender;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;

/** An account that accepted older Terms is told so by /me, and accepts the new ones. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, AuthFunctionalTestConfig.class})
class AcceptTermsFunctionalTest {

    private static final String PASSWORD = "tortoise-violin-42";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RecordingEmailSender emailSender;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void anAccountOnOlderTermsAcceptsTheNewOnes() throws Exception {
        String email = "terms-" + UUID.randomUUID() + "@x.com";
        AuthFlows.registerAndVerify(mockMvc, objectMapper, emailSender, email, PASSWORD);
        LoggedInSession session = AuthFlows.login(mockMvc, objectMapper, email, PASSWORD);
        me(session).andExpect(jsonPath("$.termsAccepted").value(true));

        jdbc.update("UPDATE users SET terms_version = '2026-01-01' WHERE email = ?", email);
        me(session).andExpect(jsonPath("$.termsAccepted").value(false));

        accept(session.accessToken(), "{\"version\":\"2026-01-01\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("TERMS_VERSION_OUTDATED"));
        accept(session.accessToken(), "{}").andExpect(status().isBadRequest());

        accept(session.accessToken(), "{\"version\":\"%s\"}".formatted(TermsVersion.CURRENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.termsAccepted").value(true));
        me(session).andExpect(jsonPath("$.termsAccepted").value(true));
    }

    @Test
    void requiresAuthentication() throws Exception {
        accept(null, "{\"version\":\"%s\"}".formatted(TermsVersion.CURRENT)).andExpect(status().isUnauthorized());
    }

    private ResultActions me(LoggedInSession session) throws Exception {
        return mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + session.accessToken()));
    }

    private ResultActions accept(String accessToken, String body) throws Exception {
        var request = put("/api/auth/me/terms").contentType(MediaType.APPLICATION_JSON).content(body);
        if (accessToken != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
        }
        return mockMvc.perform(request);
    }
}
