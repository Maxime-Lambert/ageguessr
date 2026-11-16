package app.ageguessr.shared.personaldata;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;

/** Small JDBC readings shared by the export sections. */
public final class Rows {

    private Rows() {}

    /** A TIMESTAMPTZ column, null kept. */
    public static Instant instant(ResultSet row, String column) throws SQLException {
        OffsetDateTime value = row.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    /** An INT column that may be NULL (getInt reads NULL as 0). */
    public static Integer integer(ResultSet row, String column) throws SQLException {
        int value = row.getInt(column);
        return row.wasNull() ? null : value;
    }

    /** A VARCHAR[] column, as a list (a java.sql.Array is not JSON). */
    public static List<String> strings(ResultSet row, String column) throws SQLException {
        Array array = row.getArray(column);
        return array == null ? List.of() : Arrays.asList((String[]) array.getArray());
    }
}
