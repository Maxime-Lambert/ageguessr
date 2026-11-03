package app.ageguessr.functional.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.ageguessr.TestcontainersConfiguration;
import app.ageguessr.functional.auth.AuthFlows.LoggedInSession;
import app.ageguessr.shared.email.EmailMessage;
import app.ageguessr.shared.email.RecordingEmailSender;
import java.util.UUID;
import org.hamcrest.Matchers;
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

/** The language of an account: chosen at sign-up or later, and the one its emails use. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, AuthFunctionalTestConfig.class})
class AccountLanguageFunctionalTest {

    private static final String PASSWORD = "tortoise-violin-42";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RecordingEmailSender emailSender;

    @Test
    void anAccountCreatedFromTheFrenchSiteGetsItsEmailsInFrench() throws Exception {
        String email = uniqueEmail();
        register(email, "\"FR\"").andExpect(status().isCreated());

        EmailMessage verification = emailSender.lastMessageTo(email);
        assertThat(verification.subject()).isEqualTo("Confirmez votre compte Ageguessr");
        assertThat(verification.htmlBody()).contains("http://localhost:4200/fr/confirm-email?token=");
    }

    @Test
    void anAccountCreatedWithoutALanguageHasNoneAndEnglishEmails() throws Exception {
        String email = uniqueEmail();
        register(email, null).andExpect(status().isCreated());
        assertThat(emailSender.lastMessageTo(email).htmlBody()).contains("http://localhost:4200/en/confirm-email?token=");

        LoggedInSession session = verifyAndLogIn(email);
        me(session).andExpect(jsonPath("$.language").value(Matchers.nullValue()));
    }

    @Test
    void changesTheLanguageOfTheAccountAndOfItsNextEmails() throws Exception {
        String email = uniqueEmail();
        register(email, "\"EN\"");
        LoggedInSession session = verifyAndLogIn(email);

        changeLanguage(session.accessToken(), "{\"language\":\"FR\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.language").value("FR"));
        me(session).andExpect(jsonPath("$.language").value("FR"));

        mockMvc.perform(post("/api/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\"}".formatted(email)));
        assertThat(emailSender.lastMessageTo(email).subject()).isEqualTo("Réinitialisez votre mot de passe Ageguessr");
    }

    @Test
    void refusesAnUnknownOrMissingLanguage() throws Exception {
        String email = uniqueEmail();
        register(email, null);
        LoggedInSession session = verifyAndLogIn(email);

        changeLanguage(session.accessToken(), "{\"language\":\"DE\"}").andExpect(status().isBadRequest());
        changeLanguage(session.accessToken(), "{}").andExpect(status().isBadRequest());
        register(uniqueEmail(), "\"DE\"").andExpect(status().isBadRequest());
    }

    @Test
    void requiresAuthenticationToChangeTheLanguage() throws Exception {
        mockMvc.perform(put("/api/auth/me/language")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"language\":\"FR\"}"))
                .andExpect(status().isUnauthorized());
    }

    private static String uniqueEmail() {
        return "lang-" + UUID.randomUUID() + "@x.com";
    }

    /** @param language a JSON value, or null to leave the field out */
    private ResultActions register(String email, String language) throws Exception {
        String body = "{\"email\":\"%s\",\"username\":\"%s\",\"password\":\"%s\",\"acceptTerms\":true%s}"
                .formatted(email, AuthFlows.randomUsername(), PASSWORD, language == null ? "" : ",\"language\":" + language);
        return mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private LoggedInSession verifyAndLogIn(String email) throws Exception {
        String token = VerificationLinks.extractToken(emailSender.lastMessageTo(email));
        mockMvc.perform(post("/api/auth/confirm-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"%s\"}".formatted(token)))
                .andExpect(status().isNoContent());
        return AuthFlows.login(mockMvc, objectMapper, email, PASSWORD);
    }

    private ResultActions me(LoggedInSession session) throws Exception {
        return mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + session.accessToken()));
    }

    private ResultActions changeLanguage(String accessToken, String body) throws Exception {
        return mockMvc.perform(put("/api/auth/me/language")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }
}
