import { DOCUMENT } from '@angular/common';
import { InjectionToken, inject } from '@angular/core';

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
