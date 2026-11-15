package app.ageguessr.integration.auth;

import static org.assertj.core.api.Assertions.assertThat;

import app.ageguessr.TestcontainersConfiguration;
import app.ageguessr.shared.personaldata.PersonalDataSection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The data export covers every table that holds a player's data: users itself, and every
 * table with a foreign key to it, read from the real schema. A new such table fails this
 * test until a PersonalDataSection exports it (docs/decisions/architecture.md, RGPD).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class PersonalDataCoverageIntegrationTest {

    @Autowired
    private List<PersonalDataSection> sections;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void everyTableReferencingAPlayerIsExported() {
        Set<String> referencing = new HashSet<>(jdbc.queryForList(
                """
                SELECT DISTINCT referencing.relname
                FROM pg_constraint fk
                JOIN pg_class referencing ON referencing.oid = fk.conrelid
                JOIN pg_class referenced ON referenced.oid = fk.confrelid
                WHERE fk.contype = 'f' AND referenced.relname = 'users'
                """,
                String.class));
        referencing.add("users");
        Set<String> exported = new HashSet<>();
        sections.forEach(section -> exported.addAll(section.tables()));

        assertThat(referencing).contains("game_plays", "friendships", "refresh_tokens");
        assertThat(exported).containsAll(referencing);
    }

    @Test
    void eachSectionHasItsOwnName() {
        assertThat(sections).extracting(PersonalDataSection::name).doesNotHaveDuplicates();
    }
}
