/** The API of the friends feature (ageguessr-api features/friends). */

/** A friend or a request: the id of the relation, which the actions take, and a username. */
export interface Relation {
  id: string;
  username: string;
  /** When the friendship was accepted, or when the request was sent (ISO instant). */
  since: string;
}

export interface Friends {
  friends: Relation[];
  incoming: Relation[];
  outgoing: Relation[];
}

export interface Invite {
  code: string;
}

export interface InvitePreview {
  username: string;
  own: boolean;
  alreadyFriends: boolean;
}

export type TodayStatus = 'COMPLETE' | 'IN_PROGRESS' | 'NOT_PLAYED';

export interface TodayPlayer {
  username: string;
  me: boolean;
  status: TodayStatus;
  /** Null unless COMPLETE, as max and rounds. */
  points: number | null;
  max: number | null;
  rounds: { position: number; points: number }[] | null;
}

/** Locked, and empty, until the caller's own daily game is complete. */
export interface FriendsToday {
  date: string;
  locked: boolean;
  players: TodayPlayer[];
}

export interface LeaderboardPlayer {
  /** Null for a player with no game yet, as averagePoints. */
  rank: number | null;
  username: string;
  me: boolean;
  gamesPlayed: number;
  averagePoints: number | null;
  currentStreak: number;
}

export interface FriendsLeaderboard {
  players: LeaderboardPlayer[];
}
