package app.ageguessr.features.friends.PreviewInvite;

/**
 * Who sent the link, and where the caller already stands with them, so the page offers
 * the right button.
 *
 * @param own the caller opened their own link
 */
public record InvitePreviewResponse(String username, boolean own, boolean alreadyFriends) {}
