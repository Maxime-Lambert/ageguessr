package app.ageguessr.features.people;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class WikidataIdTest {

    @ParameterizedTest
    @ValueSource(strings = {"Q1", "Q42", "Q123456789012"})
    void acceptsAnItemIdentifier(String id) {
        assertThat(WikidataId.isValid(id)).isTrue();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"Q0", "Q042", "q42", "42", "P569", "Q1234567890123", "Q42 } ?x", " Q42"})
    void refusesAnythingElse(String id) {
        assertThat(WikidataId.isValid(id)).isFalse();
    }
}
