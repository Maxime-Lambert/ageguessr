package app.ageguessr.functional.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.ageguessr.TestcontainersConfiguration;
import app.ageguessr.features.game.DailyRound;
import app.ageguessr.features.game.DailyRoundRepository;
import app.ageguessr.features.game.GuestCookie;
import app.ageguessr.features.hints.Hint;
import app.ageguessr.features.hints.HintAudience;
import app.ageguessr.features.hints.HintCategory;
import app.ageguessr.features.hints.HintRepository;
import app.ageguessr.features.hints.HintReviewStatus;
import app.ageguessr.features.people.Continent;
import app.ageguessr.features.people.Gender;
import app.ageguessr.features.people.Person;
import app.ageguessr.features.people.PersonPhoto;
import app.ageguessr.features.people.PersonPhotoRepository;
import app.ageguessr.features.people.PersonRepository;
import app.ageguessr.features.people.PhotoDateSource;
import app.ageguessr.features.people.ReviewStatus;
import app.ageguessr.functional.auth.AuthFlows;
import app.ageguessr.functional.auth.AuthFunctionalTestConfig;
import app.ageguessr.shared.email.RecordingEmailSender;
import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.ObjectMapper;

/**
 * The game endpoints end to end, against a real Postgres. They are the anti-cheat
 * surface: until a round is finished, the raw JSON must hold nothing of the answer.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, AuthFunctionalTestConfig.class})
class GameFunctionalTest {

    private static final int AGE = 40;
    private static final String PASSWORD = "tortoise-violin-42";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RecordingEmailSender emailSender;

    @Autowired
    private PersonRepository people;

    @Autowired
    private PersonPhotoRepository photos;

    @Autowired
    private HintRepository hints;

    @Autowired
    private DailyRoundRepository rounds;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private Clock clock;

    private LocalDate today;
    private final List<PersonPhoto> seeded = new ArrayList<>();

    @BeforeEach
    void seedToday() {
        today = LocalDate.now(clock);
        Continent[] boxes = {Continent.EUROPE, Continent.ASIA, Continent.AFRICA};
        for (int i = 0; i < boxes.length; i++) {
            PersonPhoto photo = savePhoto(savePerson(boxes[i], i));
            seeded.add(photo);
            rounds.save(DailyRound.builder()
                    .gameDate(today)
                    .position((short) (i + 1))
                    .photoId(photo.getId())
                    .drawnAt(Instant.now())
                    .build());
        }
        // Only MUSIC reaches the minimum for the year of the photos.
        for (int i = 0; i < Hint.MINIMUM_PER_CATEGORY; i++) {
            saveHint(HintCategory.MUSIC, today.getYear() - AGE);
        }
        saveHint(HintCategory.FILM, today.getYear() - AGE);
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM game_plays");
        jdbc.update("DELETE FROM daily_rounds");
        jdbc.update("DELETE FROM hints WHERE source_kind = 'functional-test'");
        people.deleteAll();
    }

    @Test
    void aGuestPlaysARoundToTheEndWithoutAnythingLeakingBefore() throws Exception {
        String before = mockMvc.perform(get("/api/game/today"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
                .andExpect(jsonPath("$.date").value(today.toString()))
                .andExpect(jsonPath("$.rounds.length()").value(3))
                .andExpect(jsonPath("$.rounds[0].step").value("NOT_STARTED"))
                .andExpect(jsonPath("$.rounds[0].imageUrl").value("/api/images/" + seeded.getFirst().getPublicId()))
                .andExpect(jsonPath("$.score.max").value(3000))
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertNoAnswerIn(before);

        MvcResult first = firstGuess(1, 30, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.step").value("AWAITING_HINT"))
                .andExpect(jsonPath("$.firstGuess").value(30))
                .andExpect(jsonPath("$.direction").value("OLDER"))
                .andExpect(jsonPath("$.categories.length()").value(1))
                .andExpect(jsonPath("$.categories[0]").value("MUSIC"))
                .andReturn();
        assertNoAnswerIn(first.getResponse().getContentAsString());
        String setCookie = first.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie)
                .startsWith(GuestCookie.NAME + "=")
                .contains("HttpOnly", "SameSite=Lax", "Path=/api/game", "Max-Age=2592000");
        Cookie guest = new Cookie(GuestCookie.NAME, setCookie.substring(GuestCookie.NAME.length() + 1, setCookie.indexOf(';')));

        String hint = perform(post(round(1, "hint")), "{\"category\":\"MUSIC\"}", guest, null)
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                .andExpect(jsonPath("$.step").value("AWAITING_SECOND_GUESS"))
                .andExpect(jsonPath("$.hint.category").value("MUSIC"))
                .andExpect(jsonPath("$.hint.text").value(containsString("A hit")))
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertNoAnswerIn(hint);

        String during = mockMvc.perform(get("/api/game/today").cookie(guest))
                .andExpect(jsonPath("$.rounds[0].step").value("AWAITING_SECOND_GUESS"))
                .andExpect(jsonPath("$.rounds[0].hint.text").value(containsString("A hit")))
                .andExpect(jsonPath("$.rounds[1].step").value("NOT_STARTED"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertNoAnswerIn(during);

        perform(post(round(1, "second-guess")), "{\"guess\":38}", guest, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.step").value("FINISHED"))
                .andExpect(jsonPath("$.result.age").value(AGE))
                .andExpect(jsonPath("$.result.firstPoints").value(184))
                .andExpect(jsonPath("$.result.secondPoints").value(409))
                .andExpect(jsonPath("$.result.points").value(593))
                .andExpect(jsonPath("$.result.person.name").value("Person 0"))
                .andExpect(jsonPath("$.result.credit.author").value("Jane Photographer"))
                .andExpect(jsonPath("$.result.hint.year").value(today.getYear() - AGE));

        mockMvc.perform(get("/api/game/today").cookie(guest))
                .andExpect(jsonPath("$.rounds[0].result.points").value(593))
                .andExpect(jsonPath("$.score.points").value(593))
                .andExpect(jsonPath("$.score.complete").value(false));
    }

    @Test
    void anExactFirstGuessEndsTheRoundAtAThousand() throws Exception {
        firstGuess(2, AGE, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.step").value("FINISHED"))
                .andExpect(jsonPath("$.direction").value(nullValue()))
                .andExpect(jsonPath("$.result.points").value(1000))
                .andExpect(jsonPath("$.result.secondGuess").value(nullValue()));
    }

    @Test
    void anAccountKeepsItsGameApartFromTheGuests() throws Exception {
        String email = "player-" + UUID.randomUUID() + "@x.com";
        AuthFlows.registerAndVerify(mockMvc, objectMapper, emailSender, email, PASSWORD);
        String token = AuthFlows.login(mockMvc, objectMapper, email, PASSWORD).accessToken();

        perform(post(round(1, "first-guess")), "{\"guess\":30,\"language\":\"FR\"}", null, token)
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));

        mockMvc.perform(get("/api/game/today").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(jsonPath("$.rounds[0].step").value("AWAITING_HINT"));
        mockMvc.perform(get("/api/game/today").cookie(new Cookie(GuestCookie.NAME, UUID.randomUUID().toString())))
                .andExpect(jsonPath("$.rounds[0].step").value("NOT_STARTED"));
        // The account wins over a guest cookie sent along.
        perform(post(round(1, "first-guess")), "{\"guess\":31,\"language\":\"FR\"}", guestCookie(), token)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("WRONG_STEP"));
    }

    @Test
    void anInvalidTokenIsRefusedRatherThanPlayedAsAGuest() throws Exception {
        mockMvc.perform(get("/api/game/today").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refusesASecondFirstGuess() throws Exception {
        Cookie guest = guestCookie();
        firstGuess(1, 30, guest).andExpect(status().isOk());

        firstGuess(1, 35, guest)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("WRONG_STEP"));
    }

    @Test
    void refusesTheSecondGuessBeforeTheHint() throws Exception {
        Cookie guest = guestCookie();
        firstGuess(1, 30, guest).andExpect(status().isOk());

        perform(post(round(1, "second-guess")), "{\"guess\":38}", guest, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("WRONG_STEP"));
    }

    @Test
    void refusesACategoryWithTooFewHints() throws Exception {
        Cookie guest = guestCookie();
        firstGuess(1, 30, guest).andExpect(status().isOk());

        perform(post(round(1, "hint")), "{\"category\":\"FILM\"}", guest, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("CATEGORY_UNAVAILABLE"));
    }

    @Test
    void refusesAHintToAGuestWithoutCookieAndSetsNone() throws Exception {
        perform(post(round(1, "hint")), "{\"category\":\"MUSIC\"}", null, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("WRONG_STEP"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    @Test
    void refusesTheGameOfAnotherDay() throws Exception {
        perform(post("/api/game/" + today.minusDays(1) + "/rounds/1/first-guess"), "{\"guess\":30,\"language\":\"EN\"}", null, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("GAME_CLOSED"));
    }

    @Test
    void refusesARoundWhosePhotoWasRejectedAndLeavesItOutOfTheScore() throws Exception {
        Cookie guest = guestCookie();
        firstGuess(2, 30, guest).andExpect(status().isOk());
        PersonPhoto photo = photos.findById(seeded.get(1).getId()).orElseThrow();
        photo.setReviewStatus(ReviewStatus.REJECTED);
        photos.save(photo);

        perform(post(round(2, "hint")), "{\"category\":\"MUSIC\"}", guest, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ROUND_NEUTRALIZED"));
        mockMvc.perform(get("/api/game/today").cookie(guest))
                .andExpect(jsonPath("$.rounds[1].status").value("NEUTRALIZED"))
                .andExpect(jsonPath("$.rounds[1].imageUrl").value(nullValue()))
                .andExpect(jsonPath("$.score.max").value(2000));
    }

    @Test
    void validatesTheRequest() throws Exception {
        firstGuess(1, 17, null).andExpect(status().isBadRequest());
        firstGuess(1, 101, null).andExpect(status().isBadRequest());
        perform(post(round(1, "first-guess")), "{\"guess\":30,\"language\":\"DE\"}", null, null)
                .andExpect(status().isBadRequest());
        perform(post(round(1, "first-guess")), "{\"guess\":30}", null, null).andExpect(status().isBadRequest());
        perform(post("/api/game/02-11-2026/rounds/1/first-guess"), "{\"guess\":30,\"language\":\"EN\"}", null, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
        perform(post(round(9, "first-guess")), "{\"guess\":30,\"language\":\"EN\"}", null, null)
                .andExpect(status().isNotFound());
    }

    /**
     * Neither the age, nor the identity, nor the credit (the Commons page names the
     * person), nor the internal photo id, nor the hint year (today's year minus the age).
     */
    private void assertNoAnswerIn(String json) {
        assertThat(json)
                .doesNotContain("\"age\"", "\"year\"", "\"result\":{")
                .doesNotContain("Person 0", "Jane Photographer", "commons.wikimedia.org");
        seeded.forEach(photo -> assertThat(json).doesNotContain(photo.getId().toString()));
    }

    private Cookie guestCookie() {
        return new Cookie(GuestCookie.NAME, UUID.randomUUID().toString());
    }

    private String round(int position, String step) {
        return "/api/game/" + today + "/rounds/" + position + "/" + step;
    }

    private ResultActions firstGuess(int position, int guess, Cookie guest) throws Exception {
        return perform(post(round(position, "first-guess")), "{\"guess\":%d,\"language\":\"EN\"}".formatted(guess), guest, null);
    }

    private ResultActions perform(MockHttpServletRequestBuilder request, String body, Cookie guest, String token)
            throws Exception {
        request.contentType(MediaType.APPLICATION_JSON).content(body);
        if (guest != null) {
            request.cookie(guest);
        }
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return mockMvc.perform(request);
    }

    private Person savePerson(Continent continent, int index) {
        return people.save(Person.builder()
                .wikidataId("Q" + (900_000 + index))
                .nameEn("Person " + index)
                .birthDate(LocalDate.of(1950, 1, 1))
                .sitelinks(42)
                .gender(Gender.FEMALE)
                .continent(continent)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build());
    }

    private PersonPhoto savePhoto(Person person) {
        return photos.save(PersonPhoto.builder()
                .personId(person.getId())
                .commonsFile(person.getNameEn() + ".jpg")
                .takenFrom(LocalDate.of(1990, 6, 1))
                .takenTo(LocalDate.of(1990, 6, 30))
                .ageAtPhoto(AGE)
                .dateSource(PhotoDateSource.BOTH)
                .licenseShortName("CC BY-SA 4.0")
                .authorText("Jane Photographer")
                .sourceUrl("https://commons.wikimedia.org/wiki/File:" + person.getNameEn() + ".jpg")
                .publicId(UUID.randomUUID())
                .reviewStatus(ReviewStatus.APPROVED)
                .createdAt(Instant.now())
                .build());
    }

    private void saveHint(HintCategory category, int year) {
        hints.save(Hint.builder()
                .category(category)
                .year(year)
                .audience(HintAudience.ALL)
                .textEn("A hit of that year.")
                .textFr("Un tube de cette année.")
                .sourceUrl("https://en.wikipedia.org/wiki/Hit")
                .sourceKind("functional-test")
                .dedupeKey("functional-" + UUID.randomUUID())
                .reviewStatus(HintReviewStatus.APPROVED)
                .createdAt(Instant.now())
                .build());
    }
}
