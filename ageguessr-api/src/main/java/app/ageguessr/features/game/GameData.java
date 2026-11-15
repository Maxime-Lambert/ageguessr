package app.ageguessr.features.game;

import static app.ageguessr.shared.personaldata.Rows.instant;
import static app.ageguessr.shared.personaldata.Rows.integer;
import static app.ageguessr.shared.personaldata.Rows.strings;

import app.ageguessr.shared.personaldata.PersonalDataSection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * The games of a player in the data export: what they played and guessed, never what the
 * answer was. No person, photo or hint text: a round still being played would leak it,
 * and the guesses and points are the player's own data.
 */
@Component
@Order(2)
@RequiredArgsConstructor
public class GameData implements PersonalDataSection {

    private static final String PLAY_COLUMNS =
            """
            position, language, first_guess, first_points, hint_categories, second_guess, second_points,
            photo_reported, reported_hint_categories, started_at, finished_at
            """;

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * One round played: the guesses, the points, the categories of hints asked for. The
     * points stay null until the round is finished, as in the game: with the guess and the
     * scoring formula, they would give the exact age away.
     */
    record Play(
            int position,
            String language,
            int firstGuess,
            Integer firstPoints,
            List<String> hintCategories,
            Integer secondGuess,
            Integer secondPoints,
            boolean photoReported,
            List<String> reportedHintCategories,
            Instant startedAt,
            Instant finishedAt) {}

    record DailyPlay(LocalDate date, Play round) {}

    record SeriesCreated(UUID seriesId, int size, Instant createdAt, String shareCode, Instant sharedAt) {}

    record SeriesJoined(UUID seriesId, Instant joinedAt) {}

    record SeriesPlay(UUID seriesId, Play round) {}

    record Export(
            List<DailyPlay> dailyGames,
            List<SeriesCreated> seriesCreated,
            List<SeriesJoined> seriesJoined,
            List<SeriesPlay> seriesRounds) {}

    @Override
    public String name() {
        return "games";
    }

    @Override
    public Set<String> tables() {
        return Set.of("game_plays", "free_series", "free_series_players", "free_series_plays");
    }

    @Override
    public Export export(UUID userId) {
        Map<String, UUID> id = Map.of("id", userId);
        List<DailyPlay> daily = jdbc.query(
                "SELECT game_date, " + PLAY_COLUMNS + " FROM game_plays WHERE user_id = :id ORDER BY game_date, position",
                id,
                (row, index) -> new DailyPlay(row.getObject("game_date", LocalDate.class), play(row)));
        List<SeriesCreated> created = jdbc.query(
                "SELECT id, size, created_at, share_code, shared_at FROM free_series WHERE user_id = :id ORDER BY created_at",
                id,
                (row, index) -> new SeriesCreated(
                        row.getObject("id", UUID.class),
                        row.getInt("size"),
                        instant(row, "created_at"),
                        row.getString("share_code"),
                        instant(row, "shared_at")));
        List<SeriesJoined> joined = jdbc.query(
                "SELECT series_id, joined_at FROM free_series_players WHERE user_id = :id ORDER BY joined_at",
                id,
                (row, index) -> new SeriesJoined(row.getObject("series_id", UUID.class), instant(row, "joined_at")));
        List<SeriesPlay> seriesRounds = jdbc.query(
                "SELECT series_id, " + PLAY_COLUMNS
                        + " FROM free_series_plays WHERE user_id = :id ORDER BY started_at, position",
                id,
                (row, index) -> new SeriesPlay(row.getObject("series_id", UUID.class), play(row)));
        return new Export(daily, created, joined, seriesRounds);
    }

    private static Play play(ResultSet row) throws SQLException {
        Instant finishedAt = instant(row, "finished_at");
        boolean finished = finishedAt != null;
        return new Play(
                row.getInt("position"),
                row.getString("language"),
                row.getInt("first_guess"),
                finished ? integer(row, "first_points") : null,
                strings(row, "hint_categories"),
                integer(row, "second_guess"),
                finished ? integer(row, "second_points") : null,
                row.getBoolean("photo_reported"),
                strings(row, "reported_hint_categories"),
                instant(row, "started_at"),
                finishedAt);
    }
}
