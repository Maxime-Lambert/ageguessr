package app.ageguessr.features.hints;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A fact tied to a year, from which the player works out the age on the photo: the year
 * of a hint is the current year minus that age, not the birth year, since the player does
 * not know when the photo was taken. The text never contains a year.
 */
@Entity
@Table(name = "hints")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Hint {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    private HintCategory category;

    private int year;

    @Enumerated(EnumType.STRING)
    private HintAudience audience;

    private String textEn;

    private String textFr;

    /** The page the hint comes from, for the attribution on the result screen. */
    private String sourceUrl;

    /** Kind of source ("chart", "category"...), to find the hints of a less reliable one. */
    private String sourceKind;

    /** Wikidata id of the person cited, CELEBRITIES only. */
    private String subjectQid;

    /** Stable across extractions: importing again adds only the keys not seen yet. */
    private String dedupeKey;

    @Enumerated(EnumType.STRING)
    private HintReviewStatus reviewStatus;

    /** Last curation decision: when, and by which admin (null once that account is deleted). */
    private Instant reviewedAt;

    private UUID reviewedBy;

    private Instant createdAt;
}
