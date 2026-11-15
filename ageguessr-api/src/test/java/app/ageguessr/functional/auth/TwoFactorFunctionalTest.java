package app.ageguessr.functional.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.ageguessr.TestcontainersConfiguration;
import app.ageguessr.features.auth.Totp;
import app.ageguessr.shared.email.RecordingEmailSender;
import java.time.Instant;
import java.util.Map;
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

/** Two-factor sign-in end to end: the test plays the authenticator app with Totp.codeAt. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, AuthFunctionalTestConfig.class})
class TwoFactorFunctionalTest {

    private static final String PASSWORD = "tortoise-violin-42";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RecordingEmailSender emailSender;

    @Autowired
    private JdbcTemplate jdbc;

    private record Player(UUID id, String email, String token) {}

    private Player signUp() throws Exception {
        String email = "2fa-" + UUID.randomUUID() + "@x.com";
        AuthFlows.registerAndVerify(mockMvc, objectMapper, emailSender, email, PASSWORD);
        String token = AuthFlows.login(mockMvc, objectMapper, email, PASSWORD).accessToken();
        return new Player(jdbc.queryForObject("SELECT id FROM users WHERE email = ?", UUID.class, email), email, token);
    }

    private ResultActions post(String path, String token, Map<String, String> body) throws Exception {
        var request = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body));
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return mockMvc.perform(request);
    }

    private static String codeAt(String secret, long offset) {
        return Totp.codeAt(secret, Totp.stepAt(Instant.now()) + offset);
    }

    /** Turns two-factor on and returns the secret; the code of the current step is then used. */
    private String turnOn(Player player) throws Exception {
        String json = post("/api/auth/me/two-factor/setup", player.token(), Map.of("password", PASSWORD))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String secret = objectMapper.readTree(json).get("secret").asString();
        post("/api/auth/me/two-factor/enable", player.token(), Map.of("code", codeAt(secret, 0)))
                .andExpect(status().isOk());
        return secret;
    }

    private String challenge(Player player) throws Exception {
        String json = post("/api/auth/login", null, Map.of("email", player.email(), "password", PASSWORD))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(json).get("challenge").asString();
    }

    /** Lets the next code be one already seen: the test cannot wait 30 seconds. */
    private void forgetLastStep(Player player) {
        jdbc.update("UPDATE users SET totp_last_step = NULL WHERE id = ?", player.id());
    }

    @Test
    void turningItOnTakesThePasswordThenAFirstCode() throws Exception {
        Player player = signUp();
        post("/api/auth/me/two-factor/setup", player.token(), Map.of("password", "wrong"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("INVALID_CURRENT_PASSWORD"));
        post("/api/auth/me/two-factor/enable", player.token(), Map.of("code", "123456"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("TWO_FACTOR_NOT_PENDING"));

        String json = post("/api/auth/me/two-factor/setup", player.token(), Map.of("password", PASSWORD))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.otpauthUri").value(org.hamcrest.Matchers.startsWith("otpauth://totp/AgeGuessr")))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String secret = objectMapper.readTree(json).get("secret").asString();
        String stored = jdbc.queryForObject("SELECT totp_secret FROM users WHERE id = ?", String.class, player.id());
        assertThat(stored).isNotBlank().doesNotContain(secret);

        post("/api/auth/me/two-factor/enable", player.token(), Map.of("code", "000000"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("INVALID_TOTP_CODE"));
        post("/api/auth/me/two-factor/enable", player.token(), Map.of("code", codeAt(secret, 0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.twoFactorEnabled").value(true));

        assertThat(emailSender.lastMessageTo(player.email()).subject()).contains("Two-factor authentication turned on");
        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + player.token()))
                .andExpect(jsonPath("$.twoFactorEnabled").value(true));
        post("/api/auth/me/two-factor/setup", player.token(), Map.of("password", PASSWORD))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("TWO_FACTOR_ALREADY_ENABLED"));
        // Never in the data export: only the date it was turned on.
        String export = mockMvc.perform(get("/api/auth/me/data").header(HttpHeaders.AUTHORIZATION, "Bearer " + player.token()))
                .andExpect(jsonPath("$.account.account.twoFactorEnabledAt").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(export).doesNotContain(secret, stored);
    }

    @Test
    void signingInThenAsksForACodeUsedOnceOnly() throws Exception {
        Player player = signUp();
        String secret = turnOn(player);

        String challenge = challenge(player);
        post("/api/auth/login", null, Map.of("email", player.email(), "password", PASSWORD))
                .andExpect(jsonPath("$.twoFactorRequired").value(true))
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));

        // The code that turned it on: refused, even within its 90 seconds.
        post("/api/auth/login/totp", null, Map.of("challenge", challenge, "code", codeAt(secret, 0)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("INVALID_TOTP_CODE"));
        // A typo keeps the challenge: the next code goes through.
        post("/api/auth/login/totp", null, Map.of("challenge", challenge, "code", codeAt(secret, 1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(header().exists(HttpHeaders.SET_COOKIE));
        // Used up.
        forgetLastStep(player);
        post("/api/auth/login/totp", null, Map.of("challenge", challenge, "code", codeAt(secret, 0)))
                .andExpect(status().isNotFound());
    }

    @Test
    void wrongCodesLockTheAccountLikeWrongPasswords() throws Exception {
        Player player = signUp();
        String secret = turnOn(player);
        String challenge = challenge(player);

        for (int i = 0; i < 5; i++) {
            post("/api/auth/login/totp", null, Map.of("challenge", challenge, "code", "000000"))
                    .andExpect(status().isForbidden());
        }

        post("/api/auth/login/totp", null, Map.of("challenge", challenge, "code", codeAt(secret, 1)))
                .andExpect(status().isForbidden());
    }

    @Test
    void theRightPasswordDoesNotResetTheCountOfWrongCodesWhenTurningItOff() throws Exception {
        Player player = signUp();
        String secret = turnOn(player);
        forgetLastStep(player);

        for (int i = 0; i < 5; i++) {
            post("/api/auth/me/two-factor/disable", player.token(), Map.of("password", PASSWORD, "code", "000000"))
                    .andExpect(status().isForbidden());
        }

        // Locked: even the right password and code are refused for 15 minutes.
        post("/api/auth/me/two-factor/disable", player.token(), Map.of("password", PASSWORD, "code", codeAt(secret, 0)))
                .andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT totp_enabled_at FROM users WHERE id = ?", Instant.class, player.id()))
                .isNotNull();
    }

    @Test
    void anExpiredChallengeIsRefused() throws Exception {
        Player player = signUp();
        String secret = turnOn(player);
        String challenge = challenge(player);
        jdbc.update(
                "UPDATE account_tokens SET expires_at = now() - interval '1 minute' WHERE user_id = ? AND purpose = 'TOTP_LOGIN'",
                player.id());

        post("/api/auth/login/totp", null, Map.of("challenge", challenge, "code", codeAt(secret, 1)))
                .andExpect(status().isNotFound());
    }

    @Test
    void theAppLostTheEmailedLinkTurnsItOffAndSignsEveryDeviceOut() throws Exception {
        Player player = signUp();
        turnOn(player);
        String challenge = challenge(player);

        post("/api/auth/login/totp/recovery", null, Map.of("challenge", challenge)).andExpect(status().isAccepted());
        String older = VerificationLinks.extractToken(emailSender.lastMessageTo(player.email()));
        // Asked twice: only the latest link works.
        post("/api/auth/login/totp/recovery", null, Map.of("challenge", challenge)).andExpect(status().isAccepted());
        post("/api/auth/disable-two-factor", null, Map.of("token", older)).andExpect(status().isNotFound());
        String link = emailSender.lastMessageTo(player.email()).htmlBody();
        assertThat(link).contains("/disable-two-factor?token=");
        String token = VerificationLinks.extractToken(emailSender.lastMessageTo(player.email()));

        post("/api/auth/disable-two-factor", null, Map.of("token", token)).andExpect(status().isNoContent());
        post("/api/auth/disable-two-factor", null, Map.of("token", token)).andExpect(status().isNotFound());

        assertThat(emailSender.lastMessageTo(player.email()).subject()).contains("turned off");
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM refresh_tokens WHERE user_id = ? AND revoked_at IS NULL", Integer.class, player.id()))
                .isZero();
        String json = post("/api/auth/login", null, Map.of("email", player.email(), "password", PASSWORD))
                .andExpect(jsonPath("$.accessToken").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(json).doesNotContain("challenge");
    }

    @Test
    void theRecoveryNeedsAChallengeThatIsTheRightPassword() throws Exception {
        post("/api/auth/login/totp/recovery", null, Map.of("challenge", "made-up")).andExpect(status().isNotFound());
    }

    @Test
    void turningItOffFromTheAccountTakesThePasswordAndACode() throws Exception {
        Player player = signUp();
        String secret = turnOn(player);
        forgetLastStep(player);

        post("/api/auth/me/two-factor/disable", player.token(), Map.of("password", PASSWORD, "code", "000000"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("INVALID_TOTP_CODE"));
        post("/api/auth/me/two-factor/disable", player.token(), Map.of("password", "wrong", "code", codeAt(secret, 0)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("INVALID_CURRENT_PASSWORD"));
        post("/api/auth/me/two-factor/disable", player.token(), Map.of("password", PASSWORD, "code", codeAt(secret, 0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.twoFactorEnabled").value(false));

        assertThat(jdbc.queryForObject("SELECT totp_secret FROM users WHERE id = ?", String.class, player.id())).isNull();
        post("/api/auth/me/two-factor/disable", player.token(), Map.of("password", PASSWORD, "code", codeAt(secret, 1)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("TWO_FACTOR_NOT_ENABLED"));
    }
}
