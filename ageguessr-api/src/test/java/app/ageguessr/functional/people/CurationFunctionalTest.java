package app.ageguessr.functional.people;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.ageguessr.TestcontainersConfiguration;
import app.ageguessr.features.auth.AuthTokenService;
import app.ageguessr.features.auth.User;
import app.ageguessr.features.auth.UserRepository;
import app.ageguessr.features.auth.UserRole;
import app.ageguessr.features.people.Person;
import app.ageguessr.features.people.PersonPhoto;
import app.ageguessr.features.people.PersonPhotoRepository;
import app.ageguessr.features.people.PersonRepository;
import app.ageguessr.features.people.PhotoDateSource;
import app.ageguessr.features.people.PhotoImage;
import app.ageguessr.features.people.PhotoImageRepository;
import app.ageguessr.features.people.ReviewStatus;
import app.ageguessr.functional.auth.AuthFunctionalTestConfig;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** The curation endpoints through the real security chain. app.curation.allow-living-approval is false in tests. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, AuthFunctionalTestConfig.class})
class CurationFunctionalTest {

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, 9};

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository users;

    @Autowired
    private AuthTokenService tokens;

    @Autowired
    private PersonRepository people;

    @Autowired
    private PersonPhotoRepository photos;

    @Autowired
    private PhotoImageRepository images;

    @AfterEach
    void cleanUp() {
        people.deleteAll();
    }

    @Test
    void everyCurationEndpointIsForAdminsOnly() throws Exception {
        PersonPhoto photo = store(deceased("Q1"), "A.jpg", 1976, ReviewStatus.PENDING);
        String userToken = token(UserRole.USER);
        for (MockHttpServletRequestBuilder request : new MockHttpServletRequestBuilder[] {
            get("/api/admin/photos/pending"),
            get("/api/admin/photos/" + photo.getId() + "/image"),
            reviewRequest(photo.getId(), "APPROVED"),
            get("/api/admin/people").param("query", "Jane"),
            post("/api/admin/people/" + photo.getPersonId() + "/exclusion")
        }) {
            mockMvc.perform(request).andExpect(status().isUnauthorized());
            mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void listsPendingPhotosWithWhatIsNeededToJudgeThem() throws Exception {
        Person person = deceased("Q1");
        PersonPhoto pending = store(person, "Jane 1976.jpg", 1976, ReviewStatus.PENDING);
        store(person, "Jane 1990.jpg", 1990, ReviewStatus.APPROVED);

        mockMvc.perform(asAdmin(get("/api/admin/photos/pending")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPending").value(1))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].photoId").value(pending.getId().toString()))
                .andExpect(jsonPath("$.items[0].person.wikidataId").value("Q1"))
                .andExpect(jsonPath("$.items[0].person.nameEn").value("Jane Doe"))
                .andExpect(jsonPath("$.items[0].ageAtPhoto").value(26))
                .andExpect(jsonPath("$.items[0].commonsFile").value("Jane 1976.jpg"))
                .andExpect(jsonPath("$.items[0].approvable").value(true))
                .andExpect(jsonPath("$.items[0].otherPhotos[0].ageAtPhoto").value(40))
                .andExpect(jsonPath("$.items[0].otherPhotos[0].reviewStatus").value("APPROVED"));
    }

    @Test
    void refusesAnOutOfRangeBatchSize() throws Exception {
        mockMvc.perform(asAdmin(get("/api/admin/photos/pending").param("limit", "0")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(asAdmin(get("/api/admin/photos/pending").param("limit", "51")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void showsAPendingPhotoToTheAdminWithoutCachingIt() throws Exception {
        PersonPhoto pending = store(deceased("Q1"), "A.jpg", 1976, ReviewStatus.PENDING);

        mockMvc.perform(asAdmin(get("/api/admin/photos/" + pending.getId() + "/image")))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "image/jpeg"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(content().bytes(JPEG));
        // Still hidden from the public until approved.
        mockMvc.perform(get("/api/images/" + pending.getPublicId())).andExpect(status().isNotFound());
    }

    @Test
    void approvingAPhotoPutsItInTheGame() throws Exception {
        PersonPhoto pending = store(deceased("Q1"), "A.jpg", 1976, ReviewStatus.PENDING);

        mockMvc.perform(asAdmin(reviewRequest(pending.getId(), "APPROVED")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewStatus").value("APPROVED"));

        mockMvc.perform(get("/api/images/" + pending.getPublicId())).andExpect(status().isOk());
        PersonPhoto reviewed = photos.findById(pending.getId()).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(reviewed.getReviewedBy()).isNotNull();
        org.assertj.core.api.Assertions.assertThat(reviewed.getReviewedAt()).isNotNull();
    }

    @Test
    void refusesToApproveALivingPersonButLetsTheAdminRejectIt() throws Exception {
        PersonPhoto pending = store(living("Q1"), "A.jpg", 1976, ReviewStatus.PENDING);

        mockMvc.perform(asAdmin(reviewRequest(pending.getId(), "APPROVED")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("LIVING_PERSON_APPROVAL_DISABLED"));
        mockMvc.perform(asAdmin(reviewRequest(pending.getId(), "REJECTED")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewStatus").value("REJECTED"));
    }

    @Test
    void reopeningARejectedPhotoWhoseSlotWasTakenIsAConflict() throws Exception {
        Person person = deceased("Q1");
        PersonPhoto rejected = store(person, "A.jpg", 1976, ReviewStatus.REJECTED);
        store(person, "B.jpg", 1976, ReviewStatus.PENDING);

        mockMvc.perform(asAdmin(reviewRequest(rejected.getId(), "PENDING")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("PHOTO_SLOT_TAKEN"));
    }

    @Test
    void refusesAReviewWithoutDecision() throws Exception {
        PersonPhoto pending = store(deceased("Q1"), "A.jpg", 1976, ReviewStatus.PENDING);

        mockMvc.perform(asAdmin(put("/api/admin/photos/" + pending.getId() + "/review")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void findsThePersonOfARemovalRequestThenExcludesThemAndErasesTheirPhotos() throws Exception {
        Person person = deceased("Q7");
        PersonPhoto approved = store(person, "A.jpg", 1976, ReviewStatus.APPROVED);
        store(person, "B.jpg", 1990, ReviewStatus.PENDING);

        mockMvc.perform(asAdmin(get("/api/admin/people").param("query", "jane doe")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.people[0].wikidataId").value("Q7"))
                .andExpect(jsonPath("$.people[0].photoCount").value(2))
                .andExpect(jsonPath("$.people[0].excluded").value(false));

        mockMvc.perform(asAdmin(post("/api/admin/people/" + person.getId() + "/exclusion")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deletedPhotos").value(2));

        mockMvc.perform(get("/api/images/" + approved.getPublicId())).andExpect(status().isNotFound());
        org.assertj.core.api.Assertions.assertThat(photos.findByPersonId(person.getId())).isEmpty();
        org.assertj.core.api.Assertions.assertThat(images.findById(approved.getId())).isEmpty();
        mockMvc.perform(asAdmin(get("/api/admin/people").param("query", "Q7")))
                .andExpect(jsonPath("$.people[0].excluded").value(true))
                .andExpect(jsonPath("$.people[0].photoCount").value(0));
        // Asking again is harmless.
        mockMvc.perform(asAdmin(post("/api/admin/people/" + person.getId() + "/exclusion")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deletedPhotos").value(0));
    }

    @Test
    void refusesATooShortSearch() throws Exception {
        mockMvc.perform(asAdmin(get("/api/admin/people").param("query", "J"))).andExpect(status().isBadRequest());
        mockMvc.perform(asAdmin(get("/api/admin/people"))).andExpect(status().isBadRequest());
    }

    @Test
    void excludingAnUnknownPersonIsNotFound() throws Exception {
        mockMvc.perform(asAdmin(post("/api/admin/people/" + UUID.randomUUID() + "/exclusion")))
                .andExpect(status().isNotFound());
    }

    @Test
    void reviewingAnUnknownPhotoIsNotFound() throws Exception {
        mockMvc.perform(asAdmin(reviewRequest(UUID.randomUUID(), "APPROVED"))).andExpect(status().isNotFound());
        mockMvc.perform(asAdmin(get("/api/admin/photos/" + UUID.randomUUID() + "/image")))
                .andExpect(status().isNotFound());
    }

    private MockHttpServletRequestBuilder asAdmin(MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token(UserRole.ADMIN));
    }

    private static MockHttpServletRequestBuilder reviewRequest(UUID photoId, String decision) {
        return put("/api/admin/photos/" + photoId + "/review")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\": \"" + decision + "\"}");
    }

    /** A real token, issued by the same service as at login, for a user created directly. */
    private String token(UserRole role) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        User user = users.saveAndFlush(User.builder()
                .email("curation-" + suffix + "@x.com")
                .username("cur-" + suffix)
                .passwordHash("hash")
                .emailVerified(true)
                .createdAt(Instant.now())
                .role(role)
                .build());
        return tokens.issueAccessToken(user);
    }

    private Person deceased(String wikidataId) {
        return people.saveAndFlush(person(wikidataId).deathDate(LocalDate.of(2010, 1, 1)).build());
    }

    private Person living(String wikidataId) {
        return people.saveAndFlush(person(wikidataId).build());
    }

    private static Person.PersonBuilder person(String wikidataId) {
        return Person.builder()
                .wikidataId(wikidataId)
                .nameEn("Jane Doe")
                .birthDate(LocalDate.of(1950, 3, 10))
                .sitelinks(42)
                .createdAt(Instant.now())
                .updatedAt(Instant.now());
    }

    /** A photo dated July 14 of {@code year}: after the March 10 birthday, so the age is year - 1950. */
    private PersonPhoto store(Person person, String file, int year, ReviewStatus status) {
        PersonPhoto photo = photos.saveAndFlush(PersonPhoto.builder()
                .personId(person.getId())
                .commonsFile(file)
                .takenFrom(LocalDate.of(year, 7, 14))
                .takenTo(LocalDate.of(year, 7, 14))
                .ageAtPhoto(year - 1950)
                .dateSource(PhotoDateSource.BOTH)
                .licenseShortName("CC BY-SA 4.0")
                .sourceUrl("https://commons.wikimedia.org/wiki/File:" + file)
                .publicId(UUID.randomUUID())
                .reviewStatus(status)
                .createdAt(Instant.now())
                .build());
        images.saveAndFlush(PhotoImage.builder()
                .photoId(photo.getId())
                .content(JPEG)
                .contentType("image/jpeg")
                .width(800)
                .height(1000)
                .build());
        return photo;
    }
}
