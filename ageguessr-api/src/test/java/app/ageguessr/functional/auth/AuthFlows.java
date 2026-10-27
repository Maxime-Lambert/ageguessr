package app.ageguessr.functional.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.ageguessr.shared.email.RecordingEmailSender;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

/** Shared register -> confirm -> login flow reused across functional test classes, of every feature. */
public final class AuthFlows {

    private AuthFlows() {}

    public record LoggedInSession(String accessToken, String cookieHeader) {
        /**
         * MockMvc's mock servlet request does not parse a raw "Cookie" header string
         * back into request.getCookies() the way a real servlet container would - it
         * must be attached via MockHttpServletRequestBuilder.cookie(Cookie), which is
         * what @CookieValue actually reads from. Parsed once here so every test uses
         * the correct mechanism instead of a raw header.
         */
        public Cookie asRequestCookie() {
            String pair = cookieHeader.split(";", 2)[0];
            String[] nameAndValue = pair.split("=", 2);
            return new Cookie(nameAndValue[0], nameAndValue[1]);
        }
    }

    /** Unique per call: functional tests share one database, and usernames are unique. */
    public static String randomUsername() {
        return "u-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    public static String registerBody(ObjectMapper objectMapper, String email, String username, String password) {
        return objectMapper.writeValueAsString(new RegisterPayload(email, username, password));
    }

    public static String registerBody(ObjectMapper objectMapper, String email, String password) {
        return registerBody(objectMapper, email, randomUsername(), password);
    }

    public static void registerAndVerify(
            MockMvc mockMvc, ObjectMapper objectMapper, RecordingEmailSender emailSender, String email, String password)
            throws Exception {
        registerAndVerify(mockMvc, objectMapper, emailSender, email, randomUsername(), password);
    }

    public static void registerAndVerify(
            MockMvc mockMvc,
            ObjectMapper objectMapper,
            RecordingEmailSender emailSender,
            String email,
            String username,
            String password)
            throws Exception {
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerBody(objectMapper, email, username, password)));

        String token = VerificationLinks.extractToken(emailSender.lastMessageTo(email));
        mockMvc.perform(post("/api/auth/confirm-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TokenPayload(token))))
                .andExpect(status().isNoContent());
    }

    public static LoggedInSession login(MockMvc mockMvc, ObjectMapper objectMapper, String email, String password)
            throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CredentialsPayload(email, password))))
                .andExpect(status().isOk())
                .andReturn();

        String accessToken = objectMapper
                .readValue(result.getResponse().getContentAsString(), LoginResponsePayload.class)
                .accessToken();
        String cookieHeader = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        return new LoggedInSession(accessToken, cookieHeader);
    }

    private record CredentialsPayload(String email, String password) {}

    /** Package-private: also used by RegisterFunctionalTest, which sends variations of it. */
    record RegisterPayload(String email, String username, String password, boolean acceptTerms) {
        RegisterPayload(String email, String username, String password) {
            this(email, username, password, true);
        }
    }

    private record TokenPayload(String token) {}

    private record LoginResponsePayload(String accessToken) {}
}
