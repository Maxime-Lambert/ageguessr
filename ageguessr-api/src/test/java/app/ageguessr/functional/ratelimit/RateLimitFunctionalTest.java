package app.ageguessr.functional.ratelimit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.ageguessr.TestcontainersConfiguration;
import app.ageguessr.functional.auth.AuthFunctionalTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;

/**
 * Distinct (uncached) Spring context - the lowered threshold below only applies here,
 * never to the other functional tests exercising real register/login/refresh flows.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, AuthFunctionalTestConfig.class})
@TestPropertySource(
        properties = {
            "app.rate-limit.auth.max-attempts=3",
            "app.rate-limit.auth.window-minutes=1",
            "app.rate-limit.game.max-attempts=2",
            "app.rate-limit.game.window-minutes=1",
            "app.rate-limit.series.max-attempts=2",
            "app.rate-limit.series.window-minutes=1",
            "app.rate-limit.report.max-attempts=2",
            "app.rate-limit.report.window-minutes=1",
            "app.rate-limit.friends.max-attempts=2",
            "app.rate-limit.friends.window-minutes=1"
        })
class RateLimitFunctionalTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void blocksRequestsPastTheConfiguredThreshold() throws Exception {
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/auth/resend-verification")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new EmailPayload("someone@x.com"))))
                    .andExpect(status().isAccepted());
        }

        mockMvc.perform(post("/api/auth/resend-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EmailPayload("someone@x.com"))))
                .andExpect(status().isTooManyRequests())
                .andExpect(result -> org.assertj.core.api.Assertions.assertThat(
                                result.getResponse().getContentAsString())
                        .contains("RATE_LIMITED"));
    }

    /**
     * In prod every request reaches Spring from the edge Caddy container: without
     * X-Forwarded-For (set by the Caddy chain to the real client IP) all users would
     * share a single bucket. Pins that the limiter keys on that header, not on the
     * socket peer.
     */
    @Test
    void partitionsByForwardedClientIp() throws Exception {
        String exhaustedClient = "203.0.113.10";
        for (int i = 0; i < 3; i++) {
            resendVerificationFrom(exhaustedClient).andExpect(status().isAccepted());
        }
        resendVerificationFrom(exhaustedClient).andExpect(status().isTooManyRequests());

        resendVerificationFrom("203.0.113.11").andExpect(status().isAccepted());
    }

    /**
     * The guesses have their own counter: whatever they answer (here a 409, no game being
     * drawn), past the threshold they are refused before reaching the game. The reads are
     * not limited, nor do the guesses use up the login attempts.
     */
    @Test
    void limitsTheGuessesOfTheGameApart() throws Exception {
        String client = "203.0.113.20";
        for (int i = 0; i < 2; i++) {
            guessFrom(client).andExpect(status().isConflict());
        }
        guessFrom(client).andExpect(status().isTooManyRequests());

        mockMvc.perform(get("/api/game/today").header("X-Forwarded-For", client)).andExpect(status().isOk());
        mockMvc.perform(post("/api/auth/resend-verification")
                        .header("X-Forwarded-For", client)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EmailPayload("someone@x.com"))))
                .andExpect(status().isAccepted());
    }

    /**
     * The history, the one costly read, shares the guesses' counter. Limited before the
     * security chain, so even its 401 without a token counts.
     */
    @Test
    void limitsTheHistoryWithTheGuesses() throws Exception {
        String client = "203.0.113.25";
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(get("/api/game/history").header("X-Forwarded-For", client))
                    .andExpect(status().isUnauthorized());
        }
        mockMvc.perform(get("/api/game/history").header("X-Forwarded-For", client))
                .andExpect(status().isTooManyRequests());
    }

    /**
     * Creating series and joining challenges share a tighter counter of their own (each
     * series reveals the ages of its photos), apart from the guesses.
     */
    @Test
    void limitsTheSeriesAndTheChallengesApart() throws Exception {
        String client = "203.0.113.30";
        startSeriesFrom(client).andExpect(status().isBadRequest());
        joinFrom(client).andExpect(status().isNotFound());
        startSeriesFrom(client).andExpect(status().isTooManyRequests());
        joinFrom(client).andExpect(status().isTooManyRequests());

        guessFrom(client).andExpect(status().isConflict());
    }

    /**
     * The reports of a round have their own counter too: a player who reports never uses
     * up their guesses, and a script cannot flood the admin queue.
     */
    @Test
    void limitsTheReportsApart() throws Exception {
        String client = "203.0.113.40";
        reportFrom(client, "/api/game/2000-01-01/rounds/1/report").andExpect(status().isConflict());
        reportFrom(client, "/api/game/series/" + java.util.UUID.randomUUID() + "/rounds/1/report")
                .andExpect(status().isConflict());
        reportFrom(client, "/api/game/2000-01-01/rounds/1/report").andExpect(status().isTooManyRequests());

        guessFrom(client).andExpect(status().isConflict());
    }

    /**
     * Friends have their own counter, on what a script would try in bulk: usernames and
     * invite codes. Counted before authentication, like the others; the caller's own
     * invite and the list are not limited.
     */
    @Test
    void limitsTheFriendRequestsAndInviteCodesApart() throws Exception {
        String client = "203.0.113.50";
        mockMvc.perform(post("/api/friends/requests").header("X-Forwarded-For", client))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                                "/api/friends/invite/K7F2QX9M")
                        .header("X-Forwarded-For", client))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/friends/invite/K7F2QX9M/accept").header("X-Forwarded-For", client))
                .andExpect(status().isTooManyRequests());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/friends/invite")
                        .header("X-Forwarded-For", client))
                .andExpect(status().isUnauthorized());
        guessFrom(client).andExpect(status().isConflict());
    }

    /** Today's comparison and the leaderboard load every round of a whole group: costly. */
    @Test
    void limitsTheFriendsScoresWithTheGuesses() throws Exception {
        String client = "203.0.113.60";
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/friends/today")
                        .header("X-Forwarded-For", client))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                                "/api/friends/leaderboard")
                        .header("X-Forwarded-For", client))
                .andExpect(status().isUnauthorized());
        guessFrom(client).andExpect(status().isTooManyRequests());
    }

    private ResultActions reportFrom(String clientIp, String url) throws Exception {
        return mockMvc.perform(post(url)
                .header("X-Forwarded-For", clientIp)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"target\":\"PHOTO\",\"reason\":\"OTHER\"}"));
    }

    private ResultActions startSeriesFrom(String clientIp) throws Exception {
        return mockMvc.perform(post("/api/game/series")
                .header("X-Forwarded-For", clientIp)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"size\":7}"));
    }

    private ResultActions joinFrom(String clientIp) throws Exception {
        return mockMvc.perform(post("/api/game/challenges/K7F2QX9M/join")
                .header("X-Forwarded-For", clientIp)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":\"Watson\"}"));
    }

    private ResultActions guessFrom(String clientIp) throws Exception {
        return mockMvc.perform(post("/api/game/2000-01-01/rounds/1/first-guess")
                .header("X-Forwarded-For", clientIp)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"guess\":30,\"language\":\"EN\"}"));
    }

    private ResultActions resendVerificationFrom(String clientIp)
            throws Exception {
        return mockMvc.perform(post("/api/auth/resend-verification")
                .header("X-Forwarded-For", clientIp)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new EmailPayload("someone@x.com"))));
    }

    private record EmailPayload(String email) {}
}
