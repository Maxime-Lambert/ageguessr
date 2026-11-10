package app.ageguessr.features.friends.PreviewInvite;

import app.ageguessr.features.friends.FriendErrors;
import app.ageguessr.features.friends.FriendInvite;
import app.ageguessr.features.friends.FriendInviteRepository;
import app.ageguessr.features.friends.Friendship;
import app.ageguessr.features.friends.FriendshipRepository;
import app.ageguessr.features.game.ShareCodes;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PreviewInviteHandler {

    private final FriendInviteRepository invites;
    private final FriendshipRepository friendships;

    @Transactional(readOnly = true)
    public InvitePreviewResponse handle(UUID userId, String code) {
        FriendInvite invite =
                invites.findByCode(ShareCodes.normalize(code)).orElseThrow(FriendErrors::noSuchInvite);
        UUID owner = invite.getUserId();
        String username = friendships.findUsernameOf(owner).orElseThrow(FriendErrors::noSuchInvite);
        boolean own = owner.equals(userId);
        boolean friends = !own
                && friendships.findBetween(userId, owner).map(Friendship::isAccepted).orElse(false);
        return new InvitePreviewResponse(username, own, friends);
    }
}
