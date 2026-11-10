package app.ageguessr.features.friends.GetInvite;

import app.ageguessr.features.friends.FriendInviteCodes;
import app.ageguessr.features.friends.InviteResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The caller's invite code, created on its first read. */
@Service
@RequiredArgsConstructor
public class GetInviteHandler {

    private final FriendInviteCodes codes;

    @Transactional
    public InviteResponse handle(UUID userId) {
        return new InviteResponse(codes.inviteOf(userId).getCode());
    }
}
