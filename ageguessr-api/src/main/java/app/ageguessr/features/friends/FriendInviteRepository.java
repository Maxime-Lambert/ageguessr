package app.ageguessr.features.friends;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FriendInviteRepository extends JpaRepository<FriendInvite, UUID> {

    Optional<FriendInvite> findByCode(String code);

    boolean existsByCode(String code);
}
