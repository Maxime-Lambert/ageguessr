import { Component, DestroyRef, afterNextRender, inject, signal } from '@angular/core';

/** How many pieces fall, and for how long at most (fall + delay). */
export const CONFETTI_PIECES = 40;
export const CONFETTI_MS = 2600;

const COLOURS = [
  'var(--color-score)',
  'var(--color-accent)',
  'var(--color-hot)',
  'var(--color-cold)',
  'var(--color-paper)',
];

interface Piece {
  left: number;
  delay: number;
  duration: number;
  drift: number;
  spin: number;
  colour: string;
  round: boolean;
}

/**
 * A burst of confetti over the screen, once, for an age found at the first guess: the
 * testers did not notice they had found it (2026-11-23). Home-made rather than a library:
 * forty spans and one keyframe are all it takes. Decorative only (aria-hidden, the text
 * says « Spot on »), clicks go through it, and nothing falls for a player who asked the
 * system for less motion.
 */
@Component({
  selector: 'app-confetti',
  host: {
    class: 'pointer-events-none fixed inset-0 z-50 overflow-hidden',
    'aria-hidden': 'true',
  },
  styles: `
    span {
      position: absolute;
      top: -16px;
      width: 8px;
      height: 14px;
      animation-name: fall;
      animation-timing-function: cubic-bezier(0.25, 0.5, 0.5, 1);
      animation-fill-mode: forwards;
    }
    @keyframes fall {
      to {
        transform: translate(var(--drift), 105vh) rotate(var(--spin));
        opacity: 0.6;
      }
    }
  `,
  template: `
    @for (piece of pieces(); track $index) {
      <span
        [class.rounded-full]="piece.round"
        [style.left.%]="piece.left"
        [style.background]="piece.colour"
        [style.animation-delay.ms]="piece.delay"
        [style.animation-duration.ms]="piece.duration"
        [style.--drift.px]="piece.drift"
        [style.--spin.deg]="piece.spin"
      ></span>
    }
  `,
})
export class Confetti {
  readonly pieces = signal<Piece[]>([]);

  constructor() {
    let timer: ReturnType<typeof setTimeout> | undefined;
    inject(DestroyRef).onDestroy(() => clearTimeout(timer));
    afterNextRender(() => {
      if (window.matchMedia?.('(prefers-reduced-motion: reduce)').matches) return;
      this.pieces.set(Array.from({ length: CONFETTI_PIECES }, (_, i) => piece(i)));
      timer = setTimeout(() => this.pieces.set([]), CONFETTI_MS);
    });
  }
}

function piece(index: number): Piece {
  return {
    left: Math.random() * 100,
    delay: Math.random() * 400,
    duration: 1600 + Math.random() * 600,
    drift: (Math.random() - 0.5) * 160,
    spin: 360 + Math.random() * 540,
    colour: COLOURS[index % COLOURS.length],
    round: index % 3 === 0,
  };
}
