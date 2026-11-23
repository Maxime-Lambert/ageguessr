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

/** The most one round can earn: an exact first guess. */
export const ROUND_MAX = 1000;

/**
 * One line per round, a square by points and the points (`🟨 499/1000`), never the
 * answers: a line reads better than a row of squares (testers, 2026-11-23). With `exact`
 * (the daily game only, a decision of 2026-11-13), a target for a round found at the first
 * guess, which ends it. A withdrawn photo earns nothing to compare: a black square, a dash.
 */
export function scoreLines(rounds: RoundView[], { exact = false } = {}): string {
  return rounds.map((round) => scoreLine(round, exact)).join('\n');
}

function scoreLine(round: RoundView, exact: boolean): string {
  if (round.status === 'NEUTRALIZED') {
    return '⬛ —';
  }
  const points = round.result?.points ?? 0;
  const square =
    exact && round.result && round.result.secondGuess === null
      ? '🎯'
      : points >= 900
        ? '🟩'
        : points >= 500
          ? '🟨'
          : '🟥';
  return `${square} ${points}/${ROUND_MAX}`;
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
