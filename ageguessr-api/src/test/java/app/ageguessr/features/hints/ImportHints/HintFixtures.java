package app.ageguessr.features.hints.ImportHints;

/** A valid French music line, and the variations the tests need. */
final class HintFixtures {

    private HintFixtures() {}

    static HintFileEntry french(String key) {
        return new HintFileEntry(
                "MUSIC", 1976, "FR", "Numéro un : « Une chanson ».", null,
                "https://fr.wikipedia.org/wiki/Page", "chart", null, key, false);
    }

    static HintFileEntry translated(String key) {
        return new HintFileEntry(
                "SCIENCE", 1976, "FR", "Une découverte.", null,
                "https://en.wikipedia.org/wiki/Page", "year-page", null, key, true);
    }

    static HintFileEntry withTexts(String audience, String textFr, String textEn) {
        return new HintFileEntry(
                "MUSIC", 1976, audience, textFr, textEn,
                "https://fr.wikipedia.org/wiki/Page", "chart", null, "key", false);
    }

    static HintFileEntry celebrity(String qid) {
        return new HintFileEntry(
                "CELEBRITIES", 1976, "ALL", "Une actrice.", "An actress.",
                "https://www.wikidata.org/wiki/Q42", "wikidata", qid, "key", false);
    }
}
