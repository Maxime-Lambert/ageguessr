package app.ageguessr.features.people.IngestPeople;

import static org.assertj.core.api.Assertions.assertThat;

import app.ageguessr.features.people.Continent;
import app.ageguessr.features.people.Gender;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PersonRecordTest {

    @Test
    void printsTheQidOnlySoThatNoNameReachesALog() {
        PersonRecord person = new PersonRecord(
                "Q42", 10, "Douglas Adams", "Douglas Adams", Set.of("Douglas Adams 2000.jpg"), List.of(), List.of(),
                Gender.MALE, Continent.EUROPE);

        assertThat(person).hasToString("PersonRecord[Q42]");
    }
}
