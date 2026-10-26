package app.ageguessr.features.people.IngestPeople;

import org.jsoup.Jsoup;

/**
 * Commons gives the author as HTML (links to user pages, spans, entities). We store
 * plain text only: the front end will display it as text, and it must never carry
 * markup or a link chosen by whoever edited the Commons page.
 */
final class AttributionSanitizer {

    /** Size of person_photos.author_text. */
    static final int MAX_LENGTH = 1024;

    private AttributionSanitizer() {}

    static String toPlainText(String html) {
        if (html == null) {
            return null;
        }
        // text() drops tags, decodes entities and collapses whitespace.
        String text = Jsoup.parse(html).text().strip();
        if (text.isEmpty()) {
            return null;
        }
        return text.length() <= MAX_LENGTH ? text : text.substring(0, MAX_LENGTH - 1) + "…";
    }
}
