package app.ageguessr.features.people;

import java.util.regex.Pattern;

/** A Wikidata item identifier ("Q42"), checked wherever one comes from outside before it is used. */
public final class WikidataId {

    public static final Pattern PATTERN = Pattern.compile("^Q[1-9][0-9]{0,11}$");

    private WikidataId() {}

    public static boolean isValid(String candidate) {
        return candidate != null && PATTERN.matcher(candidate).matches();
    }
}
