package app.ageguessr.features.people;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A Commons photo whose subject's age is known exactly: the shot was taken somewhere in
 * [takenFrom, takenTo] and the age is the same at both ends.
 *
 * <p>Only {@code publicId} may leave the API before a round ends. The Commons file name,
 * source URL and date would give the answer away (the file name usually contains the
 * person's name). The bytes live in {@link PhotoImage}, so loading a photo never loads
 * the image. Only APPROVED photos are served or played (see {@link ReviewStatus}).
 */
@Entity
@Table(name = "person_photos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PersonPhoto {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID personId;

    /** Commons file title without the "File:" prefix. */
    private String commonsFile;

    private LocalDate takenFrom;

    private LocalDate takenTo;

    private int ageAtPhoto;

    @Enumerated(EnumType.STRING)
    private PhotoDateSource dateSource;

    private String licenseShortName;

    private String licenseUrl;

    /** Plain text, already stripped of the HTML Commons returns. Null when Commons names no author. */
    private String authorText;

    /** The Commons file page, for attribution once the round is revealed. */
    private String sourceUrl;

    private UUID publicId;

    @Enumerated(EnumType.STRING)
    private ReviewStatus reviewStatus;

    /** Last curation decision: when, and by which admin (null once that account is deleted). */
    private Instant reviewedAt;

    private UUID reviewedBy;

    private Instant createdAt;
}
