import { DOCUMENT } from '@angular/common';
import { InjectionToken, inject } from '@angular/core';
import { RoundView } from './models';

/** How the page hands a text to the player, behind a token so the specs never touch theirs. */
export interface ShareTarget {
  /** The system share sheet (phones), when the browser has one. */
  canShare(): boolean;
  share(text: string): Promise<void>;
  copy(text: string): Promise<void>;
  /** The address of the site, without the language: Caddy sends each friend to theirs. */
  origin(): string;
}

export const SHARE_TARGET = new InjectionToken<ShareTarget>('ShareTarget', {
  factory: () => {
    const window = inject(DOCUMENT).defaultView!;
    return {
      canShare: () => typeof window.navigator.share === 'function',
      share: (text) => window.navigator.share({ text }),
      copy: (text) => window.navigator.clipboard.writeText(text),
      origin: () => window.location.origin,
    };
  },
});

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
