import { Friends, FriendsLeaderboard, FriendsToday, Relation } from './models';

export function relation(username: string, id = `id-${username}`): Relation {
  return { id, username, since: '2026-11-08T10:00:00Z' };
}

export function friendsOf(overrides: Partial<Friends> = {}): Friends {
  return { friends: [], incoming: [], outgoing: [], ...overrides };
}

export function lockedToday(): FriendsToday {
  return { date: '2026-11-10', locked: true, players: [] };
}

export function board(overrides: Partial<FriendsLeaderboard> = {}): FriendsLeaderboard {
  return {
    players: [
      { rank: 1, username: 'me', me: true, gamesPlayed: 4, averagePoints: 2100, currentStreak: 2 },
    ],
    ...overrides,
  };
}
