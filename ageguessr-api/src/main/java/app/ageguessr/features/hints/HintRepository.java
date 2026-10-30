package app.ageguessr.features.hints;

import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface HintRepository extends JpaRepository<Hint, UUID> {
    @Query("SELECT h.dedupeKey FROM Hint h")
    Set<String> findAllDedupeKeys();
}
