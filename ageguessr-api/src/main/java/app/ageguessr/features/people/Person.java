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
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A public figure sourced from Wikidata. Personal data even though it is public:
 * {@code excluded} records a takedown request or an editorial-line exclusion
 * ({@code exclusionReason} tells which, V10). Setting it deletes the person's photos and
 * images in the database itself (trigger in V4__create_people_tables.sql); the row stays
 * so that the ingestion never adds the person back.
 */
@Entity
@Table(name = "people")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Person {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Wikidata QID, e.g. "Q42". */
    private String wikidataId;

    private String nameEn;

    private String nameFr;

    private LocalDate birthDate;

    private LocalDate deathDate;

    private int sitelinks;

    /** Null when Wikidata does not tell. Used only to balance the pool. */
    @Enumerated(EnumType.STRING)
    private Gender gender;

    /** Continent of nationality, null when Wikidata does not tell. Used only to balance the pool. */
    @Enumerated(EnumType.STRING)
    private Continent continent;

    // No setter: exclude(reason) sets both together, as the V10 constraint requires.
    @Setter(AccessLevel.NONE)
    private boolean excluded;

    /** Set exactly when {@code excluded} is (database constraint, V10). */
    @Setter(AccessLevel.NONE)
    @Enumerated(EnumType.STRING)
    private ExclusionReason exclusionReason;

    /** Marks the person excluded: the V4 trigger deletes their photos and images. */
    public void exclude(ExclusionReason reason) {
        this.excluded = true;
        this.exclusionReason = reason;
    }

    private Instant createdAt;

    private Instant updatedAt;
}
