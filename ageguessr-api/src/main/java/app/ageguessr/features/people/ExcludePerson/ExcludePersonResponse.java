package app.ageguessr.features.people.ExcludePerson;

import java.util.UUID;

/** {@code deletedPhotos}: what the exclusion erased, to confirm it to the person who asked. */
public record ExcludePersonResponse(UUID personId, int deletedPhotos) {}
