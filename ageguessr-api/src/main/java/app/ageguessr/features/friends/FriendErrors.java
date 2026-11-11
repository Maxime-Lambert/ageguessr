package app.ageguessr.features.friends;

import app.ageguessr.shared.exceptions.ConflictException;
import app.ageguessr.shared.exceptions.NotFoundException;

/**
 * The business refusals of the friends graph. A relation that is not the caller's is a
 * 404, the same as an unknown id: a 403 would tell that the id exists.
 */
public final class FriendErrors {

    private FriendErrors() {}

    /** Unknown, unverified, or oneself: the same answer, so nothing is learnt by trying. */
    public static NotFoundException noSuchPlayer() {
        return new NotFoundException("No player with this username");
    }

    public static NotFoundException noSuchRequest() {
        return new NotFoundException("No such friend request");
    }

    public static NotFoundException noSuchFriend() {
        return new NotFoundException("No such friend");
    }

    public static ConflictException alreadyFriends() {
        return new ConflictException("ALREADY_FRIENDS", "You are already friends");
    }

    public static ConflictException requestAlreadySent() {
        return new ConflictException("REQUEST_ALREADY_SENT", "You already sent a request to this player");
    }

    /** Never merged into a friendship: accepting takes an explicit click (user decision). */
    public static ConflictException incomingRequestExists() {
        return new ConflictException("INCOMING_REQUEST_EXISTS", "This player already sent you a request");
    }

    public static ConflictException friendLimitReached() {
        return new ConflictException("FRIEND_LIMIT_REACHED", "You reached the maximum number of friends");
    }

    public static ConflictException otherFriendLimitReached() {
        return new ConflictException(
                "OTHER_FRIEND_LIMIT_REACHED", "This player reached the maximum number of friends");
    }

    public static ConflictException pendingLimitReached() {
        return new ConflictException(
                "PENDING_LIMIT_REACHED", "Too many requests waiting for an answer, cancel some first");
    }
}
