package app.ageguessr.integration.game;

import static org.assertj.core.api.Assertions.assertThat;

import app.ageguessr.TestcontainersConfiguration;
import java.sql.Array;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The data migrations of feature/better-hints-gameplay (V20, V21), on rows written in the format of
 * before: the application schema is already migrated, so each test builds its own schema
 * up to the version just before, fills it, then applies the migration under test.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class HintMigrationsIntegrationTest {

    private static final String SCHEMA = "hint_migrations_check";

    @Autowired
    private DataSource dataSource;

    private JdbcTemplate jdbc;

    @BeforeEach
    void freshSchema() {
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
    }

    @AfterEach
    void dropSchema() {
        jdbc.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
    }

    @Test
    void v20TurnsTheHintOfEachRoundIntoAListOfOneAndKeepsItsReport() {
        migrateTo("19");
        UUID hint = insertHint("MUSIC");
        jdbc.update("INSERT INTO " + SCHEMA + ".daily_rounds (game_date, position, drawn_at) VALUES ('2026-11-02', 1, now())");
        UUID withHint = insertDailyPlay("'MUSIC'", "'" + hint + "'", true);
        UUID withoutHint = insertDailyPlay("NULL", "NULL", false);
        // A hint deleted under ON DELETE SET NULL: the category stays, the id is gone.
        UUID deletedHint = insertDailyPlay("'FILM'", "NULL", false);
        UUID series = jdbc.queryForObject(
                "INSERT INTO " + SCHEMA + ".free_series (size, created_on, guest_id) VALUES (5, '2026-11-02', gen_random_uuid())"
                        + " RETURNING id",
                UUID.class);
        jdbc.update("INSERT INTO " + SCHEMA + ".free_series_rounds (series_id, position) VALUES (?, 1)", series);
        UUID seriesPlay = jdbc.queryForObject(
                "INSERT INTO " + SCHEMA + ".free_series_plays"
                        + " (series_id, position, guest_id, language, first_guess, first_points, hint_category, hint_id)"
                        + " VALUES (?, 1, gen_random_uuid(), 'FR', 30, 184, 'MUSIC', ?) RETURNING id",
                UUID.class,
                series,
                hint);

        migrateTo("20");

        assertThat(hintsOf("game_plays", withHint)).containsExactly(List.of("MUSIC"), List.of(hint.toString()), List.of("MUSIC"));
        assertThat(hintsOf("game_plays", withoutHint)).containsExactly(List.of(), List.of(), List.of());
        assertThat(hintsOf("game_plays", deletedHint)).containsExactly(List.of("FILM"), Arrays.asList((String) null), List.of());
        assertThat(hintsOf("free_series_plays", seriesPlay))
                .containsExactly(List.of("MUSIC"), List.of(hint.toString()), List.of());
    }

    @Test
    void v21RewordsTheCelebrityHintsOnlyAndOnlyOnce() {
        migrateTo("20");
        UUID celebrity = insertHint(
                "CELEBRITIES", "ALL", "Born that year: Jane Doe, American actress, singer.",
                "Personnalité née cette année-là : Jane Doe, actrice et chanteuse américaine.", "Q42");
        UUID corrected = insertHint("CELEBRITIES", "ALL", "Jane Roe turns that age this year.", "Jane Roe a cet âge cette année.", "Q43");
        UUID music = insertHint("MUSIC", "FR", null, "Personnalité née cette année-là : pas une célébrité.", null);

        migrateTo("21");

        assertThat(textsOf(celebrity))
                .containsExactly(
                        "On the photo, this person is as old as this celebrity turns this year: Jane Doe, American actress, singer.",
                        "Sur la photo, cette personne a l'âge qu'a cette année Jane Doe, actrice et chanteuse américaine.");
        assertThat(textsOf(corrected)).containsExactly("Jane Roe turns that age this year.", "Jane Roe a cet âge cette année.");
        assertThat(textsOf(music)).containsExactly(null, "Personnalité née cette année-là : pas une célébrité.");
    }

    private void migrateTo(String version) {
        Flyway.configure()
                .dataSource(dataSource)
                .schemas(SCHEMA)
                .locations("classpath:db/migration")
                .target(version)
                .load()
                .migrate();
    }

    private UUID insertHint(String category) {
        return insertHint(category, "EN", "A hit.", null, null);
    }

    private UUID insertHint(String category, String audience, String textEn, String textFr, String subjectQid) {
        return jdbc.queryForObject(
                "INSERT INTO " + SCHEMA + ".hints (category, year, audience, text_en, text_fr, subject_qid, source_url,"
                        + " source_kind, dedupe_key, review_status) VALUES (?, 1986, ?, ?, ?, ?,"
                        + " 'https://en.wikipedia.org/wiki/Hit', 'test', gen_random_uuid()::text, 'APPROVED') RETURNING id",
                UUID.class,
                category,
                audience,
                textEn,
                textFr,
                subjectQid);
    }

    /** The English then the French text of a hint. */
    private List<String> textsOf(UUID hint) {
        Map<String, Object> row = jdbc.queryForMap("SELECT text_en, text_fr FROM " + SCHEMA + ".hints WHERE id = ?", hint);
        return Arrays.asList((String) row.get("text_en"), (String) row.get("text_fr"));
    }

    private UUID insertDailyPlay(String category, String hintId, boolean reported) {
        return jdbc.queryForObject(
                "INSERT INTO " + SCHEMA + ".game_plays (game_date, position, guest_id, language, first_guess, first_points,"
                        + " hint_category, hint_id, hint_reported) VALUES ('2026-11-02', 1, gen_random_uuid(), 'EN', 30, 184, "
                        + category + ", " + hintId + "::uuid, " + reported + ") RETURNING id",
                UUID.class);
    }

    /** The categories, the hint ids and the categories reported, as strings. */
    private List<List<String>> hintsOf(String table, UUID id) {
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT hint_categories, hint_ids, reported_hint_categories FROM " + SCHEMA + "." + table + " WHERE id = ?",
                id);
        return List.of(
                strings(row.get("hint_categories")), strings(row.get("hint_ids")), strings(row.get("reported_hint_categories")));
    }

    private static List<String> strings(Object array) {
        try {
            Object[] values = (Object[]) ((Array) array).getArray();
            return Arrays.stream(values).map(value -> value == null ? null : value.toString()).toList();
        } catch (java.sql.SQLException e) {
            throw new IllegalStateException(e);
        }
    }
}
