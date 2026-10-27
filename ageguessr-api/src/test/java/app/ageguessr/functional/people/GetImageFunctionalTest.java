package app.ageguessr.functional.people;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.ageguessr.TestcontainersConfiguration;
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
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, AuthFunctionalTestConfig.class})
class GetImageFunctionalTest {

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, 1, 2, 3};

    @Autowired
    private MockMvc mockMvc;

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
    void servesAnApprovedImageToAGuestWithoutRevealingItsSource() throws Exception {
        UUID publicId = storePhoto("Q1", false, ReviewStatus.APPROVED);

        mockMvc.perform(get("/api/images/" + publicId))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "image/jpeg"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "max-age=86400, public"))
                .andExpect(header().doesNotExist(HttpHeaders.CONTENT_DISPOSITION))
                .andExpect(header().doesNotExist(HttpHeaders.ETAG))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(content().bytes(JPEG));
    }

    @Test
    void answersTheSame404ForEveryKindOfMissingImage() throws Exception {
        UUID pending = storePhoto("Q1", false, ReviewStatus.PENDING);
        UUID rejected = storePhoto("Q2", false, ReviewStatus.REJECTED);
        UUID excluded = storePhoto("Q3", true, ReviewStatus.APPROVED);

        for (String id : new String[] {
            pending.toString(), rejected.toString(), excluded.toString(), UUID.randomUUID().toString(), "not-a-uuid",
            "Portrait_of_Jane_Doe.jpg"
        }) {
            mockMvc.perform(get("/api/images/" + id)).andExpect(status().isNotFound());
        }
    }

    @Test
    void theInternalPhotoIdIsNotAPublicId() throws Exception {
        UUID publicId = storePhoto("Q1", false, ReviewStatus.APPROVED);
        UUID internalId = photos.findAll().stream()
                .filter(p -> p.getPublicId().equals(publicId))
                .findFirst()
                .orElseThrow()
                .getId();

        mockMvc.perform(get("/api/images/" + internalId)).andExpect(status().isNotFound());
    }

    @Test
    void onlyGetIsOpenToGuests() throws Exception {
        UUID publicId = storePhoto("Q1", false, ReviewStatus.APPROVED);

        mockMvc.perform(post("/api/images/" + publicId)).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/images/" + publicId)).andExpect(status().isUnauthorized());
    }

    /** Guards the allow-list: only /api/images/{id} was opened, not everything under it. */
    @Test
    void deeperPathsStillRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/images/" + UUID.randomUUID() + "/metadata")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/images")).andExpect(status().isUnauthorized());
    }

    private UUID storePhoto(String wikidataId, boolean excluded, ReviewStatus status) {
        Person person = people.saveAndFlush(Person.builder()
                .wikidataId(wikidataId)
                .nameEn("Jane Doe")
                .birthDate(LocalDate.of(1950, 3, 10))
                .sitelinks(42)
                .excluded(excluded)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build());
        PersonPhoto photo = photos.saveAndFlush(PersonPhoto.builder()
                .personId(person.getId())
                .commonsFile("Portrait of Jane Doe.jpg")
                .takenFrom(LocalDate.of(1976, 7, 14))
                .takenTo(LocalDate.of(1976, 7, 14))
                .ageAtPhoto(26)
                .dateSource(PhotoDateSource.BOTH)
                .licenseShortName("CC BY-SA 4.0")
                .sourceUrl("https://commons.wikimedia.org/wiki/File:Portrait_of_Jane_Doe.jpg")
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
        return photo.getPublicId();
    }
}
