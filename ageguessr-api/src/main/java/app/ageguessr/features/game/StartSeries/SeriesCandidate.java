package app.ageguessr.features.game.StartSeries;

import app.ageguessr.features.people.Continent;
import app.ageguessr.features.people.Gender;
import java.util.UUID;

/** A photo a series may be drawn from, with what the variety rules look at. */
public record SeriesCandidate(UUID photoId, UUID personId, Gender gender, Continent continent) {}
