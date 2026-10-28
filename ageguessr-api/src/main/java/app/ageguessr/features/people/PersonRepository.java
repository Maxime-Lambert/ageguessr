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

    /** QIDs of every person still in the pool, for the editorial line to check. */
    @Query("SELECT p.wikidataId FROM Person p WHERE p.excluded = false")
    List<String> findWikidataIdsNotExcluded();

    /** Photos with one review status of the people of one gender and continent. */
    record CellCount(Gender gender, Continent continent, ReviewStatus status, long photos) {}

    /**
     * Photos of the pool balance per review status: of people not excluded whose gender
     * and continent are known.
     */
    @Query("""
            SELECT new app.ageguessr.features.people.PersonRepository$CellCount(
                p.gender, p.continent, ph.reviewStatus, COUNT(ph))
            FROM Person p, PersonPhoto ph
            WHERE ph.personId = p.id
              AND p.excluded = false AND p.gender IS NOT NULL AND p.continent IS NOT NULL
            GROUP BY p.gender, p.continent, ph.reviewStatus
            """)
    List<CellCount> countPhotosByCell();
}
