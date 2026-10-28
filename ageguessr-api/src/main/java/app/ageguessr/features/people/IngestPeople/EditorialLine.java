package app.ageguessr.features.people.IngestPeople;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * The editorial line as Wikidata criteria: who never enters the game, whatever their
 * photos (plans/done/editorial-line.md). Criteria only, never a list of names: the
 * repository is public, and naming someone next to a crime is not ours to publish.
 * Each criterion is one QLever query listing the QIDs it catches among all humans.
 */
final class EditorialLine {

    enum Criterion {
        /** Convicted of (P1399) or charged with (P1595) one of {@link #CRIMES}, subclasses included. */
        CONVICTED_OR_CHARGED,
        /** Occupation (P106) dictator. Rarely used on Wikidata: the leaders criterion does the work. */
        DICTATOR,
        /** Member (P102) of a party aligned or ideologically far right. */
        FAR_RIGHT,
        /** Held (P39) a head of state or head of government position of an authoritarian state. */
        AUTHORITARIAN_LEADER
    }

    /**
     * War crime, crime against humanity, genocide, terrorism, rape, sexual assault, sexual
     * abuse, child sexual abuse, child pornography, sex trafficking, statutory rape, human
     * trafficking, murder. Not "convicted of" in general: it flags Alan Turing (gross
     * indecency) or tax fraud.
     */
    static final Set<String> CRIMES = Set.of(
            "Q135010", "Q173462", "Q41397", "Q7283", "Q47092", "Q673281", "Q43414", "Q26553", "Q660567",
            "Q21129531", "Q1968926", "Q181784", "Q132821");

    static final String DICTATOR = "Q183318";

    /** Political alignment (P1387): right-wing extremism, far-right, alt-right. */
    static final Set<String> FAR_RIGHT_ALIGNMENTS = Set.of("Q204481", "Q127869500", "Q23018230");

    /** Political ideology (P1142): fascism, neo-fascism, Nazism, neo-Nazism, white nationalism, white supremacy. */
    static final Set<String> FAR_RIGHT_IDEOLOGIES = Set.of("Q6223", "Q696116", "Q7310", "Q151250", "Q278929", "Q698752");

    /** Head of state, head of government: their subclasses are the positions (President of Russia...). */
    static final Set<String> LEADER_ROLES = Set.of("Q48352", "Q2285706");

    /**
     * States rated "authoritarian regime" (score under 4) by the EIU Democracy Index 2020,
     * as read from Wikidata (P8328) on 2026-10-28, 2020 being the best covered year (163
     * countries); plus Libya and Afghanistan, rated authoritarian but missing there, and
     * defunct states: Soviet Union, East Germany, SFR and FR Yugoslavia, Zaire, Ba'athist
     * Iraq. Today's rating applies to every past leader, on purpose: a leader of a then
     * democratic period is excluded too (user decision, 2026-10-28).
     */
    static final Set<String> AUTHORITARIAN_STATES = Set.of(
            "Q1000", "Q1006", "Q1007", "Q1009", "Q1016", "Q1025", "Q1029", "Q1032", "Q1037", "Q1049", "Q1050",
            "Q115", "Q148", "Q15180", "Q159", "Q16957", "Q184", "Q219060", "Q227", "Q232", "Q241", "Q262", "Q265",
            "Q3108185", "Q398", "Q423", "Q424", "Q6500954", "Q657", "Q717", "Q79", "Q794", "Q796", "Q805", "Q810",
            "Q811", "Q817", "Q819", "Q83286", "Q836", "Q838261", "Q842", "Q846", "Q851", "Q858", "Q863", "Q874",
            "Q878", "Q881", "Q889", "Q912", "Q916", "Q929", "Q945", "Q954", "Q965", "Q967", "Q970", "Q971", "Q974",
            "Q977", "Q983", "Q986");

    private EditorialLine() {}

    /** The QLever query listing every human ({@code ?person}) the criterion catches. */
    static String query(Criterion criterion) {
        String where = switch (criterion) {
            case CONVICTED_OR_CHARGED -> "VALUES ?crime { " + entities(CRIMES) + " }\n"
                    + "?charge wdt:P279* ?crime .\n"
                    + "{ ?person wdt:P1399 ?charge } UNION { ?person wdt:P1595 ?charge }";
            case DICTATOR -> "?person wdt:P106 wd:" + DICTATOR + " .";
            case FAR_RIGHT -> "{ VALUES ?alignment { " + entities(FAR_RIGHT_ALIGNMENTS) + " }"
                    + " ?party wdt:P1387 ?alignment }\n"
                    + "UNION { VALUES ?ideology { " + entities(FAR_RIGHT_IDEOLOGIES) + " }"
                    + " ?party wdt:P1142 ?ideology }\n"
                    + "?person wdt:P102 ?party .";
            case AUTHORITARIAN_LEADER -> "VALUES ?state { " + entities(AUTHORITARIAN_STATES) + " }\n"
                    + "VALUES ?role { " + entities(LEADER_ROLES) + " }\n"
                    + "?position wdt:P279* ?role .\n"
                    + "{ ?position wdt:P1001 ?state } UNION { ?position wdt:P17 ?state }\n"
                    + "?person wdt:P39 ?position .";
        };
        return "SELECT DISTINCT ?person WHERE {\n" + where + "\n?person wdt:P31 wd:Q5 .\n}";
    }

    /** Sorted, so that a query is always the same text. */
    private static String entities(Set<String> qids) {
        return qids.stream().sorted().map(q -> "wd:" + q).collect(Collectors.joining(" "));
    }
}
