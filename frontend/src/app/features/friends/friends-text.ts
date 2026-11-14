import { HttpErrorResponse } from '@angular/common/http';
import { ApiErrorResponse } from '../../shared/api-error';
import { COMMON_MESSAGES } from '../../shared/common-messages';

/** Every text of the friends pages, translated once (messages.fr.xlf). */
export const FRIENDS_TEXT = {
  title: $localize`:@@friends.title:Friends`,
  loading: $localize`:@@friends.loading:Loading…`,
  retry: $localize`:@@friends.retry:Try again`,
  loadError: $localize`:@@friends.loadError:Your friends could not be loaded.`,
  addTitle: $localize`:@@friends.addTitle:Add a friend`,
  usernameLabel: $localize`:@@friends.usernameLabel:Their exact username`,
  usernameHint: $localize`:@@friends.usernameHint:Usernames are not searchable: ask your friend for theirs.`,
  send: $localize`:@@friends.send:Send the request`,
  sent: (username: string) => $localize`:@@friends.sent:Request sent to ${username}:username:.`,
  incomingTitle: $localize`:@@friends.incomingTitle:Requests received`,
  outgoingTitle: $localize`:@@friends.outgoingTitle:Requests sent`,
  friendsTitle: $localize`:@@friends.friendsTitle:My friends`,
  accept: $localize`:@@friends.accept:Accept`,
  decline: $localize`:@@friends.decline:Decline`,
  cancel: $localize`:@@friends.cancel:Cancel`,
  remove: $localize`:@@friends.remove:Remove`,
  removeConfirm: (username: string) =>
    $localize`:@@friends.removeConfirm:Remove ${username}:username: from your friends?`,
  confirm: $localize`:@@friends.confirm:Yes`,
  keep: $localize`:@@friends.keep:No`,
  noFriends: $localize`:@@friends.noFriends:No friend yet. Send them a request with their username.`,
  todayTitle: $localize`:@@friends.todayTitle:Today's game`,
  todayLocked: $localize`:@@friends.todayLocked:Finish today's game to see your friends' scores.`,
  playToday: $localize`:@@friends.playToday:Play today's game`,
  inProgress: $localize`:@@friends.inProgress:In progress`,
  notPlayed: $localize`:@@friends.notPlayed:Not played yet`,
  outOf: $localize`:@@friends.outOf:out of`,
  round: $localize`:@@friends.round:Round`,
  points: $localize`:@@friends.points:points`,
  you: $localize`:@@friends.you:you`,
  leaderboardTitle: $localize`:@@friends.leaderboardTitle:Leaderboard`,
  leaderboardHelp: $localize`:@@friends.leaderboardHelp:Average score per daily game finished.`,
  rank: $localize`:@@friends.rank:Rank`,
  player: $localize`:@@friends.player:Player`,
  average: $localize`:@@friends.average:Average`,
  games: $localize`:@@friends.games:Games`,
  streak: $localize`:@@friends.streak:Streak`,
  noGame: $localize`:@@friends.noGame:No game yet`,
  aloneOnBoard: $localize`:@@friends.aloneOnBoard:Add friends to compare your scores.`,
};

export const NO_SUCH_PLAYER = $localize`:@@friends.error.noSuchPlayer:No player with this username.`;
/** A request or a friend gone meanwhile (cancelled, declined, removed on the other side). */
export const GONE = $localize`:@@friends.error.gone:This request or friend is no longer there.`;

/**
 * What to tell the player for a refused action: one of our texts, never the API's own.
 * @param notFound what a 404 means here: an unknown username, or a relation gone
 */
export function friendsError(error: HttpErrorResponse, notFound = GONE): string {
  const code = (error.error as ApiErrorResponse | null)?.errorCode;
  if (error.status === 429) return COMMON_MESSAGES.tooManyAttempts;
  if (error.status === 404) return notFound;
  // The username does not have the format of one: it names nobody.
  if (error.status === 400) return NO_SUCH_PLAYER;
  switch (code) {
    case 'ALREADY_FRIENDS':
      return $localize`:@@friends.error.alreadyFriends:You are already friends.`;
    case 'REQUEST_ALREADY_SENT':
      return $localize`:@@friends.error.alreadySent:You already sent a request to this player.`;
    case 'INCOMING_REQUEST_EXISTS':
      return $localize`:@@friends.error.incoming:This player already sent you a request: accept it in the requests received.`;
    case 'FRIEND_LIMIT_REACHED':
      return $localize`:@@friends.error.limit:You reached the maximum number of friends.`;
    case 'OTHER_FRIEND_LIMIT_REACHED':
      return $localize`:@@friends.error.otherLimit:This player reached the maximum number of friends.`;
    case 'PENDING_LIMIT_REACHED':
      return $localize`:@@friends.error.pendingLimit:Too many requests waiting for an answer. Cancel some first.`;
    default:
      return COMMON_MESSAGES.genericError;
  }
}
