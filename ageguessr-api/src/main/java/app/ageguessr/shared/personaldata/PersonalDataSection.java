package app.ageguessr.shared.personaldata;

import java.util.Set;
import java.util.UUID;

/**
 * One part of a player's data export (GDPR access and portability, GET /api/auth/me/data).
 * Each feature exports its own tables with one bean of this type, so that the export,
 * assembled in auth, imports no other feature (ArchitectureTests) and only the feature
 * that owns a table knows its columns. Beans are assembled in their {@code @Order}.
 *
 * <p>Rules for an implementation: read only; never a secret (password or token hash);
 * never the answer of a round (person, photo, hint text), which a round still being played
 * would leak. Every table with a column referencing {@code users(id)} must be listed by
 * some section's {@link #tables()}: PersonalDataCoverageIntegrationTest fails otherwise.
 */
public interface PersonalDataSection {

    /** The key of this part in the exported JSON. */
    String name();

    /** The tables this part exports, for the coverage test. */
    Set<String> tables();

    /** This player's data, serialized as JSON; an empty list or object when there is none. */
    Object export(UUID userId);
}
