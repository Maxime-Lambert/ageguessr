package app.ageguessr.features.friends.SendFriendRequest;

import app.ageguessr.shared.validation.Username;

/** The exact username, whatever its case: no partial search, the accounts cannot be listed. */
public record SendFriendRequestCommand(@Username String username) {}
