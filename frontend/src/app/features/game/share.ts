import { ShareTarget } from '../../shared/share-target';
import { RoundView } from './models';

/** The link to a challenge, without the language prefix: Caddy picks each friend's. */
export function challengeUrl(origin: string, code: string): string {
  return `${origin}/challenge/${code}`;
}

/** The link of the daily game: the site itself, Caddy sending each player to their language. */
export function dailyShareUrl(origin: string): string {
  return origin;
}

/**
 * One square per round, by points: never the answers. With `exact` (the daily game only, a
 * decision of 2026-11-13), a target for a round found at the first guess, which ends it.
 */
export function squares(rounds: RoundView[], { exact = false } = {}): string {
  return rounds
    .map((round) => {
      if (round.status === 'NEUTRALIZED') {
        return '⬛';
      }
      if (exact && round.result && round.result.secondGuess === null) {
        return '🎯';
      }
      const points = round.result?.points ?? 0;
      return points >= 900 ? '🟩' : points >= 500 ? '🟨' : '🟥';
    })
    .join('');
}

/** The format of a nickname, as the API checks it (the format of a username). */
export const NICKNAME_PATTERN = /^[A-Za-z0-9_-]{3,20}$/;

/**
 * The system share sheet when the browser has one (phones), else the clipboard. A share
 * the player cancels is no error; only a refused copy rejects.
 */
export async function shareOrCopy(target: ShareTarget, text: string): Promise<'shared' | 'copied'> {
  if (target.canShare()) {
    await target.share(text).catch(() => undefined);
    return 'shared';
  }
  await target.copy(text);
  return 'copied';
}
