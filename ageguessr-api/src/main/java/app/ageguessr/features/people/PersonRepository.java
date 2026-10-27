package app.ageguessr.features.people;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PersonRepository extends JpaRepository<Person, UUID> {
    Optional<Person> findByWikidataId(String wikidataId);

    /**
     * People whose English or French name contains {@code fragment}, ignoring case and
     * accents, best-known first. {@code fragment} must already have its LIKE wildcards
     * escaped with a backslash.
     */
    @Query(
            value = """
                    SELECT * FROM people
                    WHERE unaccent(lower(coalesce(name_en, ''))) LIKE unaccent(lower('%' || :fragment || '%')) ESCAPE '\\'
                       OR unaccent(lower(coalesce(name_fr, ''))) LIKE unaccent(lower('%' || :fragment || '%')) ESCAPE '\\'
                    ORDER BY sitelinks DESC, id
                    LIMIT :limit
                    """,
            nativeQuery = true)
    List<Person> searchByName(@Param("fragment") String fragment, @Param("limit") int limit);

    /** Photos in play (pending or approved) of the people of one gender and continent. */
    record CellCount(Gender gender, Continent continent, long photos) {}

    /**
     * Photos counting toward the pool balance: still in play, of people not excluded whose
     * gender and continent are known.
     */
    @Query("""
            SELECT new app.ageguessr.features.people.PersonRepository$CellCount(p.gender, p.continent, COUNT(ph))
            FROM Person p, PersonPhoto ph
            WHERE ph.personId = p.id
              AND p.excluded = false AND p.gender IS NOT NULL AND p.continent IS NOT NULL
              AND ph.reviewStatus <> app.ageguessr.features.people.ReviewStatus.REJECTED
            GROUP BY p.gender, p.continent
            """)
    List<CellCount> countPhotosByCell();
}
