package app.ageguessr.features.people.ListReportedPhotos;

import app.ageguessr.features.people.Person;
import app.ageguessr.features.people.PersonPhoto;
import app.ageguessr.features.people.PersonPhotoRepository;
import app.ageguessr.features.people.PersonRepository;
import app.ageguessr.features.people.PhotoReportCount;
import app.ageguessr.features.people.PhotoReportReason;
import app.ageguessr.features.people.PhotoReportRepository;
import app.ageguessr.shared.reports.ReportTally;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The queue of reported photos: only open reports count, most reported first. A whole
 * queue at once, without paging: reports are rare, and each one is closed by a decision.
 */
@Service
@RequiredArgsConstructor
public class ListReportedPhotosHandler {

    private final PhotoReportRepository reports;
    private final PersonPhotoRepository photos;
    private final PersonRepository people;
    private final Clock clock;

    @Transactional(readOnly = true)
    public ReportedPhotosResponse handle() {
        List<ReportTally<PhotoReportReason>> tallies = ReportTally.of(
                PhotoReportReason.class,
                reports.countOpen().stream().map(PhotoReportCount::asCount).toList());
        Map<UUID, PersonPhoto> photosById = photos.findAllById(tallies.stream().map(ReportTally::targetId).toList())
                .stream()
                .collect(Collectors.toMap(PersonPhoto::getId, Function.identity()));
        Map<UUID, Person> peopleById = people.findAllById(
                        photosById.values().stream().map(PersonPhoto::getPersonId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(Person::getId, Function.identity()));
        Set<UUID> today = new HashSet<>(reports.findPhotosPlayedOn(LocalDate.now(clock)));
        List<ReportedPhotosResponse.Item> items = tallies.stream()
                .map(tally -> item(tally, photosById.get(tally.targetId()), peopleById, today))
                .filter(Objects::nonNull)
                .toList();
        return new ReportedPhotosResponse(items);
    }

    private static ReportedPhotosResponse.Item item(
            ReportTally<PhotoReportReason> tally, PersonPhoto photo, Map<UUID, Person> peopleById, Set<UUID> today) {
        Person person = photo == null ? null : peopleById.get(photo.getPersonId());
        if (person == null) {
            // Deleted between the two reads: its reports go with it (CASCADE).
            return null;
        }
        return new ReportedPhotosResponse.Item(
                photo.getId(),
                photo.getReviewStatus(),
                photo.getAgeAtPhoto(),
                photo.getSourceUrl(),
                new ReportedPhotosResponse.PersonView(
                        person.getId(), person.getWikidataId(), person.getNameEn(), person.getNameFr()),
                tally.reasons(),
                tally.total(),
                tally.firstAt(),
                today.contains(photo.getId()));
    }
}
