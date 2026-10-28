package app.ageguessr.features.people.ExcludePerson;

import app.ageguessr.features.people.ExclusionReason;
import app.ageguessr.features.people.Person;
import app.ageguessr.features.people.PersonPhotoRepository;
import app.ageguessr.features.people.PersonRepository;
import app.ageguessr.shared.exceptions.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles a removal request: marks the person excluded, which makes the V4 trigger delete
 * their photos and images, and keeps the row so the ingestion never adds them back.
 * Idempotent; there is no way back from the interface.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExcludePersonHandler {

    private final PersonRepository people;
    private final PersonPhotoRepository photos;

    @Transactional
    public ExcludePersonResponse handle(ExcludePersonCommand command) {
        Person person = people.findById(command.personId()).orElseThrow(() -> new NotFoundException("Person not found"));
        if (person.isExcluded()) {
            return new ExcludePersonResponse(person.getId(), 0);
        }
        int photoCount = photos.findByPersonId(person.getId()).size();
        person.exclude(ExclusionReason.MANUAL);
        people.saveAndFlush(person);
        // QID only: the point of the request is that the name stops appearing anywhere.
        log.info("{}: excluded on request, {} photo(s) deleted", person.getWikidataId(), photoCount);
        return new ExcludePersonResponse(person.getId(), photoCount);
    }
}
