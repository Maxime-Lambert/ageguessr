package app.ageguessr.features.people;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PersonPhotoRepository extends JpaRepository<PersonPhoto, UUID> {
    List<PersonPhoto> findByPersonId(UUID personId);
}
