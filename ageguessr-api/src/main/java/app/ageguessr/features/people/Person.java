package app.ageguessr.features.people;

import jakarta.persistence.Entity;
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
 * A public figure sourced from Wikidata. Personal data even though it is public:
 * {@code excluded} records a takedown request. Setting it deletes the person's photos and
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

    private boolean excluded;

    private Instant createdAt;

    private Instant updatedAt;
}
