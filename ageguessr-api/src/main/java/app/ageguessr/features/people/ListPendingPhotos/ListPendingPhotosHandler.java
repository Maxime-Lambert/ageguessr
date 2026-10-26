package app.ageguessr.features.people.ListPendingPhotos;

import app.ageguessr.features.people.CurationProperties;
import app.ageguessr.features.people.PendingPhoto;
import app.ageguessr.features.people.Person;
import app.ageguessr.features.people.PersonPhoto;
import app.ageguessr.features.people.PersonPhotoRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ListPendingPhotosHandler {

    private final PersonPhotoRepository photos;
    private final CurationProperties curation;

    @Transactional(readOnly = true)
    public PendingPhotosResponse handle(ListPendingPhotosQuery query) {
        List<PendingPhoto> pending = photos.findPending(PageRequest.ofSize(query.limit()));
        // Siblings of every person in the batch, in one query rather than one per photo.
        Map<UUID, List<PersonPhoto>> byPerson = photos
                .findByPersonIdIn(pending.stream().map(p -> p.person().getId()).distinct().toList())
                .stream()
                .collect(Collectors.groupingBy(PersonPhoto::getPersonId));
        List<PendingPhotosResponse.Item> items = pending.stream()
                .map(p -> item(p, byPerson.getOrDefault(p.person().getId(), List.of())))
                .toList();
        return new PendingPhotosResponse(photos.countPending(), items);
    }

    private PendingPhotosResponse.Item item(PendingPhoto pending, List<PersonPhoto> siblings) {
        PersonPhoto photo = pending.photo();
        Person person = pending.person();
        return new PendingPhotosResponse.Item(
                photo.getId(),
                new PendingPhotosResponse.PersonView(
                        person.getId(),
                        person.getWikidataId(),
                        person.getNameEn(),
                        person.getNameFr(),
                        person.getBirthDate(),
                        person.getDeathDate(),
                        person.getSitelinks()),
                photo.getTakenFrom(),
                photo.getTakenTo(),
                photo.getAgeAtPhoto(),
                photo.getDateSource(),
                photo.getCommonsFile(),
                photo.getSourceUrl(),
                photo.getLicenseShortName(),
                photo.getLicenseUrl(),
                photo.getAuthorText(),
                curation.canApprove(person),
                siblings.stream()
                        .filter(s -> !s.getId().equals(photo.getId()))
                        .sorted((a, b) -> a.getTakenFrom().compareTo(b.getTakenFrom()))
                        .map(s -> new PendingPhotosResponse.OtherPhoto(
                                s.getId(), s.getTakenFrom(), s.getAgeAtPhoto(), s.getReviewStatus()))
                        .toList());
    }
}
