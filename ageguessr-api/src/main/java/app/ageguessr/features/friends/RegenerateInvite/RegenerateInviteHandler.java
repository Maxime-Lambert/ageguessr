package app.ageguessr.features.friends.RegenerateInvite;

import app.ageguessr.features.friends.FriendInviteCodes;
import app.ageguessr.features.friends.InviteResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** A new code, for a link shared too widely: the old one stops working at once. */
@Service
@RequiredArgsConstructor
public class RegenerateInviteHandler {

    private final FriendInviteCodes codes;

    @Transactional
    public InviteResponse handle(UUID userId) {
        return new InviteResponse(codes.replace(userId).getCode());
    }
}
