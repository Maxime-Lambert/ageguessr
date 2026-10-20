package app.ageguessr.functional.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.ageguessr.TestcontainersConfiguration;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, AuthFunctionalTestConfig.class})
class ChangeUsernameFunctionalTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RecordingEmailSender emailSender;

    @Test
    void changesTheUsernameVisibleInTheProfile() throws Exception {
        LoggedInSession session = registeredSession(AuthFlows.randomUsername());
        String newUsername = AuthFlows.randomUsername();

        changeUsername(session, newUsername)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(newUsername));

        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + session.accessToken()))
                .andExpect(jsonPath("$.username").value(newUsername));
    }

    @Test
    void rejectsAUsernameTakenByAnotherUser() throws Exception {
        String taken = AuthFlows.randomUsername();
        registeredSession(taken);
        LoggedInSession session = registeredSession(AuthFlows.randomUsername());

        changeUsername(session, taken.toUpperCase())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("USERNAME_TAKEN"));
    }

    @Test
    void rejectsAnInvalidUsername() throws Exception {
        LoggedInSession session = registeredSession(AuthFlows.randomUsername());

        changeUsername(session, "no spaces").andExpect(status().isBadRequest());
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(patch("/api/auth/me/username")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UsernamePayload("whatever"))))
                .andExpect(status().isUnauthorized());
    }

    private LoggedInSession registeredSession(String username) throws Exception {
        String email = "rename-" + UUID.randomUUID() + "@x.com";
        AuthFlows.registerAndVerify(mockMvc, objectMapper, emailSender, email, username, "tortoise-violin-42");
        return AuthFlows.login(mockMvc, objectMapper, email, "tortoise-violin-42");
    }

    private ResultActions changeUsername(LoggedInSession session, String username) throws Exception {
        return mockMvc.perform(patch("/api/auth/me/username")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + session.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new UsernamePayload(username))));
    }

    private record UsernamePayload(String username) {}
}
