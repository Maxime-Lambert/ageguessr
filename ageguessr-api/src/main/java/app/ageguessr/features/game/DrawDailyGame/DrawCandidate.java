package app.ageguessr.features.game.DrawDailyGame;

import app.ageguessr.features.people.Continent;
import app.ageguessr.features.people.Gender;
import java.time.LocalDate;
import java.util.UUID;

/**
 * An approved photo the draw may pick, with the box (continent × gender) of its person
 * and the last day it was played, null if never. Gender and continent are null when
 * Wikidata does not tell; the draw then treats null as a box of its own.
 */
public record DrawCandidate(UUID photoId, Gender gender, Continent continent, LocalDate lastPlayedOn) {}
