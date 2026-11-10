import { RoundView } from './models';

/** The link to a challenge, without the language prefix: Caddy picks each friend's. */
export function challengeUrl(origin: string, code: string): string {
  return `${origin}/challenge/${code}`;
}

/** One square per round, by points: never the answers. */
export function squares(rounds: RoundView[]): string {
  return rounds
    .map((round) => {
      if (round.status === 'NEUTRALIZED') {
        return '⬛';
      }
      const points = round.result?.points ?? 0;
      return points >= 900 ? '🟩' : points >= 500 ? '🟨' : '🟥';
    })
    .join('');
}

/** The format of a nickname, as the API checks it (the format of a username). */
export const NICKNAME_PATTERN = /^[A-Za-z0-9_-]{3,20}$/;
