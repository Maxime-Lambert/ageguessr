package app.ageguessr.functional.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.ageguessr.TestcontainersConfiguration;
import app.ageguessr.shared.email.RecordingEmailSender;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

/** GET /api/auth/me/data: the account, sessions and friends parts (the games part: GameFunctionalTest). */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, AuthFunctionalTestConfig.class})
class ExportPersonalDataFunctionalTest {

    private static final String PASSWORD = "tortoise-violin-42";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RecordingEmailSender emailSender;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private Clock clock;

    private record Player(UUID id, String email, String username, String token) {}

    private Player signUp() throws Exception {
        String email = "export-" + UUID.randomUUID() + "@x.com";
        String username = AuthFlows.randomUsername();
        AuthFlows.registerAndVerify(mockMvc, objectMapper, emailSender, email, username, PASSWORD);
        String token = AuthFlows.login(mockMvc, objectMapper, email, PASSWORD).accessToken();
        UUID id = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", UUID.class, email);
        return new Player(id, email, username, token);
    }

    @Test
    void handsThePlayerTheirAccountSessionsLinksAndFriendsAsAFile() throws Exception {
        Player me = signUp();
        Player friend = signUp();
        Player stranger = signUp();
        Player asking = signUp();
        jdbc.update(
                "INSERT INTO friendships (requester_id, addressee_id, status, accepted_at) VALUES (?, ?, 'ACCEPTED', now())",
                me.id(), friend.id());
        jdbc.update(
                "INSERT INTO friendships (requester_id, addressee_id, status) VALUES (?, ?, 'PENDING')",
                asking.id(), me.id());

        String json = mockMvc.perform(get("/api/auth/me/data").header(HttpHeaders.AUTHORIZATION, "Bearer " + me.token()))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"ageguessr-data-" + LocalDate.now(clock) + ".json\""))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.exportedAt").exists())
                .andExpect(jsonPath("$.account.account.email").value(me.email()))
                .andExpect(jsonPath("$.account.account.username").value(me.username()))
                .andExpect(jsonPath("$.account.account.emailVerified").value(true))
                .andExpect(jsonPath("$.account.account.role").value("USER"))
                .andExpect(jsonPath("$.account.sessions.length()").value(1))
                .andExpect(jsonPath("$.account.emailLinks[0].purpose").value("EMAIL_VERIFICATION"))
                .andExpect(jsonPath("$.account.emailLinks[0].usedAt").exists())
                .andExpect(jsonPath("$.games.dailyGames.length()").value(0))
                .andExpect(jsonPath("$.friends.length()").value(2))
                .andExpect(jsonPath("$.friends[0].username").value(friend.username()))
                .andExpect(jsonPath("$.friends[0].status").value("ACCEPTED"))
                .andExpect(jsonPath("$.friends[0].requestSent").value(true))
                .andExpect(jsonPath("$.friends[1].username").value(asking.username()))
                .andExpect(jsonPath("$.friends[1].requestSent").value(false))
                .andExpect(jsonPath("$.photoReviews.length()").value(0))
                .andExpect(jsonPath("$.hintReviews.length()").value(0))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String passwordHash = jdbc.queryForObject("SELECT password_hash FROM users WHERE id = ?", String.class, me.id());
        String sessionHash =
                jdbc.queryForObject("SELECT token_hash FROM refresh_tokens WHERE user_id = ?", String.class, me.id());
        assertThat(json)
                .doesNotContainIgnoringCase("hash")
                .doesNotContain(passwordHash, sessionHash)
                // Other players: by username only, and nothing of a stranger.
                .doesNotContain(friend.email(), asking.email(), stranger.email(), stranger.username())
                .doesNotContain(me.id().toString(), friend.id().toString());
    }

    @Test
    void needsAnAccount() throws Exception {
        mockMvc.perform(get("/api/auth/me/data")).andExpect(status().isUnauthorized());
    }

    @Test
    void answersNotFoundOnceTheAccountIsGone() throws Exception {
        Player gone = signUp();
        jdbc.update("DELETE FROM users WHERE id = ?", gone.id());

        mockMvc.perform(get("/api/auth/me/data").header(HttpHeaders.AUTHORIZATION, "Bearer " + gone.token()))
                .andExpect(status().isNotFound());
    }
}
