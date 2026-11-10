package app.ageguessr.functional.friends;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.ageguessr.TestcontainersConfiguration;
import app.ageguessr.features.game.DailyRound;
import app.ageguessr.features.game.DailyRoundRepository;
import app.ageguessr.features.game.GamePlay;
import app.ageguessr.features.game.GamePlayRepository;
import app.ageguessr.features.game.PlayerKey;
import app.ageguessr.functional.auth.AuthFlows;
import app.ageguessr.functional.auth.AuthFunctionalTestConfig;
import app.ageguessr.shared.email.RecordingEmailSender;
import app.ageguessr.shared.i18n.Language;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.ObjectMapper;

/**
 * The friends graph end to end, through the real security chain: who may act on a
 * relation, and nothing of an account but its username ever leaves the server.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, AuthFunctionalTestConfig.class})
class FriendsFunctionalTest {

    private static final String PASSWORD = "tortoise-violin-42";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RecordingEmailSender emailSender;

    @Autowired
    private JdbcTemplate jdbc;

    private record Account(String email, String username, String token) {}

    @Autowired
    private DailyRoundRepository dailyRounds;

    @Autowired
    private GamePlayRepository plays;

    @Autowired
    private Clock clock;

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM game_plays");
        jdbc.update("DELETE FROM daily_rounds");
        jdbc.update("DELETE FROM users WHERE email LIKE 'friends-%'");
    }

    /**
     * Today's comparison unlocks once the caller's own game is complete, and only shows
     * points: never a guess, a name or an age. The round has no photo here, so it reads as
     * neutralized: finished, it keeps its points (PlayerHistory).
     */
    @Test
    void comparesTodaysScoresOnceTheCallersGameIsComplete() throws Exception {
        LocalDate today = LocalDate.now(clock);
        dailyRounds.save(DailyRound.builder()
                .gameDate(today)
                .position((short) 1)
                .drawnAt(Instant.now())
                .build());
        Account alice = account();
        Account bob = account();
        Account carol = account();
        Account stranger = account();
        befriend(alice, bob);
        befriend(carol, alice);
        playExactly(bob, today);
        playExactly(stranger, today);

        perform(get("/api/friends/today"), alice, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.locked").value(true))
                .andExpect(jsonPath("$.players.length()").value(0));

        playExactly(alice, today);
        String body = perform(get("/api/friends/today"), alice, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.locked").value(false))
                .andExpect(jsonPath("$.date").value(today.toString()))
                .andExpect(jsonPath("$.players.length()").value(3))
                .andExpect(jsonPath("$.players[2].username").value(carol.username()))
                .andExpect(jsonPath("$.players[2].status").value("NOT_PLAYED"))
                .andExpect(jsonPath("$.players[2].points").isEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(body).contains(bob.username(), "\"points\":1000", "\"max\":1000");
        assertThat(body).doesNotContain(stranger.username()).doesNotContain("guess").doesNotContain("age\"");
        assertNothingPrivateIn(body, bob);

        String board = perform(get("/api/friends/leaderboard"), alice, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.players.length()").value(3))
                .andExpect(jsonPath("$.players[0].rank").value(1))
                .andExpect(jsonPath("$.players[1].rank").value(1))
                .andExpect(jsonPath("$.players[0].averagePoints").value(1000))
                .andExpect(jsonPath("$.players[2].username").value(carol.username()))
                .andExpect(jsonPath("$.players[2].rank").isEmpty())
                .andExpect(jsonPath("$.players[2].gamesPlayed").value(0))
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(board).doesNotContain(stranger.username());
        assertNothingPrivateIn(board, carol);
    }

    @Test
    void aloneOnTheLeaderboardWithoutFriends() throws Exception {
        Account alice = account();

        perform(get("/api/friends/leaderboard"), alice, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.players.length()").value(1))
                .andExpect(jsonPath("$.players[0].me").value(true))
                .andExpect(jsonPath("$.players[0].rank").isEmpty());
    }

    private void befriend(Account owner, Account other) throws Exception {
        String code = objectMapper
                .readTree(perform(get("/api/friends/invite"), owner, null).andReturn().getResponse().getContentAsString())
                .get("code")
                .asString();
        perform(post("/api/friends/invite/" + code + "/accept"), other, null).andExpect(status().isNoContent());
    }

    /** An exact first guess: a finished round worth 1 000. */
    private void playExactly(Account account, LocalDate day) {
        UUID id = UUID.fromString(
                jdbc.queryForObject("SELECT id::text FROM users WHERE email = ?", String.class, account.email()));
        plays.saveAndFlush(GamePlay.start(day, (short) 1, PlayerKey.account(id), Language.EN, 40, 40, Instant.now()));
    }

    @Test
    void everyEndpointNeedsAnAccount() throws Exception {
        mockMvc.perform(get("/api/friends")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/friends/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"someone\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/friends/invite")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/friends/invite/K7F2QX9M")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/friends/invite/K7F2QX9M/accept")).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/friends/" + UUID.randomUUID())).andExpect(status().isUnauthorized());
    }

    @Test
    void sendAcceptThenRemove() throws Exception {
        Account alice = account();
        Account bob = account();

        perform(post("/api/friends/requests"), alice, "{\"username\":\"" + bob.username().toUpperCase() + "\"}")
                .andExpect(status().isNoContent());
        perform(post("/api/friends/requests"), alice, "{\"username\":\"" + bob.username() + "\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("REQUEST_ALREADY_SENT"));
        perform(post("/api/friends/requests"), bob, "{\"username\":\"" + alice.username() + "\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("INCOMING_REQUEST_EXISTS"));

        String aliceList = list(alice)
                .andExpect(jsonPath("$.outgoing[0].username").value(bob.username()))
                .andExpect(jsonPath("$.friends.length()").value(0))
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertNothingPrivateIn(aliceList, bob);
        String requestId = objectMapper.readTree(list(bob).andReturn().getResponse().getContentAsString())
                .get("incoming")
                .get(0)
                .get("id")
                .asString();

        perform(post("/api/friends/requests/" + requestId + "/accept"), alice, null)
                .andExpect(status().isNotFound());
        perform(post("/api/friends/requests/" + requestId + "/accept"), bob, null)
                .andExpect(status().isNoContent());
        list(alice).andExpect(jsonPath("$.friends[0].username").value(bob.username()))
                .andExpect(jsonPath("$.outgoing.length()").value(0));
        list(bob).andExpect(jsonPath("$.friends[0].username").value(alice.username()))
                .andExpect(jsonPath("$.incoming.length()").value(0));

        Account stranger = account();
        perform(delete("/api/friends/" + requestId), stranger, null).andExpect(status().isNotFound());
        perform(delete("/api/friends/" + requestId), alice, null).andExpect(status().isNoContent());
        list(bob).andExpect(jsonPath("$.friends.length()").value(0));
    }

    @Test
    void declinedSilentlyThenSentAgain() throws Exception {
        Account alice = account();
        Account bob = account();
        perform(post("/api/friends/requests"), alice, "{\"username\":\"" + bob.username() + "\"}");
        String requestId = objectMapper.readTree(list(bob).andReturn().getResponse().getContentAsString())
                .get("incoming")
                .get(0)
                .get("id")
                .asString();

        perform(delete("/api/friends/requests/" + requestId), bob, null).andExpect(status().isNoContent());

        list(alice).andExpect(jsonPath("$.outgoing.length()").value(0));
        perform(post("/api/friends/requests"), alice, "{\"username\":\"" + bob.username() + "\"}")
                .andExpect(status().isNoContent());
    }

    @Test
    void anUnknownUsernameOneselfAndAnUnverifiedAccountLookTheSame() throws Exception {
        Account alice = account();
        String unverified = AuthFlows.randomUsername();
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(AuthFlows.registerBody(objectMapper, "friends-unverified@x.com", unverified, PASSWORD)));

        for (String username : new String[] {"nobody-here", alice.username(), unverified}) {
            perform(post("/api/friends/requests"), alice, "{\"username\":\"" + username + "\"}")
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("No player with this username"));
        }
    }

    @Test
    void addsAFriendThroughTheirInviteLink() throws Exception {
        Account alice = account();
        Account bob = account();
        String code = objectMapper
                .readTree(perform(get("/api/friends/invite"), alice, null)
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .get("code")
                .asString();
        perform(get("/api/friends/invite"), alice, null).andExpect(jsonPath("$.code").value(code));

        String preview = perform(get("/api/friends/invite/" + code.toLowerCase()), bob, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(alice.username()))
                .andExpect(jsonPath("$.own").value(false))
                .andExpect(jsonPath("$.alreadyFriends").value(false))
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertNothingPrivateIn(preview, alice);
        perform(post("/api/friends/invite/" + code + "/accept"), alice, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("OWN_INVITE"));
        perform(post("/api/friends/invite/" + code + "/accept"), bob, null).andExpect(status().isNoContent());
        perform(post("/api/friends/invite/" + code + "/accept"), bob, null).andExpect(status().isNoContent());
        list(bob).andExpect(jsonPath("$.friends[0].username").value(alice.username()));

        String newCode = objectMapper
                .readTree(perform(post("/api/friends/invite"), alice, null)
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .get("code")
                .asString();
        assertThat(newCode).isNotEqualTo(code);
        perform(get("/api/friends/invite/" + code), bob, null).andExpect(status().isNotFound());
        perform(get("/api/friends/invite/" + newCode), bob, null)
                .andExpect(jsonPath("$.alreadyFriends").value(true));
    }

    @Test
    void deletingAnAccountRemovesItFromItsFriends() throws Exception {
        Account alice = account();
        Account bob = account();
        String code = objectMapper
                .readTree(perform(get("/api/friends/invite"), alice, null).andReturn().getResponse().getContentAsString())
                .get("code")
                .asString();
        perform(post("/api/friends/invite/" + code + "/accept"), bob, null).andExpect(status().isNoContent());

        jdbc.update("DELETE FROM users WHERE email = ?", alice.email());

        list(bob).andExpect(jsonPath("$.friends.length()").value(0));
    }

    private Account account() throws Exception {
        String email = "friends-" + UUID.randomUUID() + "@x.com";
        String username = AuthFlows.randomUsername();
        AuthFlows.registerAndVerify(mockMvc, objectMapper, emailSender, email, username, PASSWORD);
        return new Account(email, username, AuthFlows.login(mockMvc, objectMapper, email, PASSWORD).accessToken());
    }

    private ResultActions list(Account account) throws Exception {
        return perform(get("/api/friends"), account, null).andExpect(status().isOk());
    }

    private ResultActions perform(MockHttpServletRequestBuilder request, Account account, String body)
            throws Exception {
        request.header(HttpHeaders.AUTHORIZATION, "Bearer " + account.token());
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mockMvc.perform(request);
    }

    /** A friend is a username: never their email nor their account id. */
    private void assertNothingPrivateIn(String body, Account other) {
        String id = jdbc.queryForObject("SELECT id::text FROM users WHERE email = ?", String.class, other.email());
        assertThat(body).doesNotContain(other.email()).doesNotContain(id);
    }
}
