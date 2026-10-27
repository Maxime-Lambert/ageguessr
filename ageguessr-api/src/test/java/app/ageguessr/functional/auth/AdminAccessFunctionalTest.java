package app.ageguessr.functional.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.ageguessr.TestcontainersConfiguration;
import app.ageguessr.features.auth.UserRepository;
import app.ageguessr.features.auth.UserRole;
import app.ageguessr.functional.auth.AuthFlows.LoggedInSession;
import app.ageguessr.shared.email.RecordingEmailSender;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The /api/admin/** rule in SecurityConfig, on a path with no handler behind it: getting
 * past security therefore shows up as a 404, being stopped by it as a 401 or 403.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, AuthFunctionalTestConfig.class})
class AdminAccessFunctionalTest {

    private static final String PASSWORD = "tortoise-violin-42";
    private static final String ADMIN_PATH = "/api/admin/nothing-here";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RecordingEmailSender emailSender;

    @Autowired
    private UserRepository userRepository;

    @Test
    void anonymousCallersAreUnauthorized() throws Exception {
        mockMvc.perform(get(ADMIN_PATH)).andExpect(status().isUnauthorized());
    }

    @Test
    void regularUsersAreForbidden() throws Exception {
        LoggedInSession session = loggedIn(UserRole.USER);

        mockMvc.perform(get(ADMIN_PATH).header(HttpHeaders.AUTHORIZATION, bearer(session)))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminsGetThrough() throws Exception {
        LoggedInSession session = loggedIn(UserRole.ADMIN);

        mockMvc.perform(get(ADMIN_PATH).header(HttpHeaders.AUTHORIZATION, bearer(session)))
                .andExpect(status().isNotFound());
    }

    @Test
    void meTellsTheFrontEndTheRole() throws Exception {
        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(loggedIn(UserRole.USER))))
                .andExpect(jsonPath("$.role").value("USER"));
        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(loggedIn(UserRole.ADMIN))))
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void aPromotionDoneInSqlAppliesFromTheNextRefresh() throws Exception {
        String email = "promoted-" + UUID.randomUUID() + "@x.com";
        AuthFlows.registerAndVerify(mockMvc, objectMapper, emailSender, email, PASSWORD);
        LoggedInSession session = AuthFlows.login(mockMvc, objectMapper, email, PASSWORD);
        setRole(email, UserRole.ADMIN);

        // The access token in hand was issued before the promotion.
        mockMvc.perform(get(ADMIN_PATH).header(HttpHeaders.AUTHORIZATION, bearer(session)))
                .andExpect(status().isForbidden());

        MvcResult refreshed = mockMvc.perform(post("/api/auth/refresh").cookie(session.asRequestCookie()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(refreshed.getResponse().getContentAsString());
        mockMvc.perform(get(ADMIN_PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + body.get("accessToken").asString()))
                .andExpect(status().isNotFound());
    }

    private LoggedInSession loggedIn(UserRole role) throws Exception {
        String email = "role-" + UUID.randomUUID() + "@x.com";
        AuthFlows.registerAndVerify(mockMvc, objectMapper, emailSender, email, PASSWORD);
        setRole(email, role);
        return AuthFlows.login(mockMvc, objectMapper, email, PASSWORD);
    }

    private void setRole(String email, UserRole role) {
        var user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        user.setRole(role);
        userRepository.save(user);
    }

    private static String bearer(LoggedInSession session) {
        return "Bearer " + session.accessToken();
    }
}
