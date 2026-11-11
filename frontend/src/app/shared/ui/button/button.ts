import { Directive, booleanAttribute, computed, input } from '@angular/core';

export type ButtonVariant =
  'primary' | 'secondary' | 'ghost' | 'danger' | 'paper-secondary' | 'paper-ghost';
export type ButtonSize = 'md' | 'sm';

// Full class strings, never assembled from pieces: Tailwind only generates the classes it
// finds written out in the sources.
const BASE =
  'inline-flex items-center justify-center gap-2 rounded-md font-semibold whitespace-nowrap ' +
  'transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 ' +
  'focus-visible:outline-accent disabled:cursor-not-allowed disabled:opacity-50';

const SIZES: Record<ButtonSize, string> = {
  md: 'h-11 px-5 text-[15px]',
  sm: 'h-9 px-3.5 text-sm',
};

const VARIANTS: Record<ButtonVariant, string> = {
  primary: 'bg-primary text-primary-foreground hover:opacity-90',
  secondary: 'border border-input-border text-foreground hover:bg-surface-2',
  ghost: 'text-muted hover:bg-surface-2 hover:text-foreground',
  danger: 'bg-danger text-danger-foreground hover:opacity-90',
  // On the hint's newspaper clipping (bg-paper), whose colors are not the page's: the page's
  // foreground is light in the dark theme, so it would vanish on the paper.
  'paper-secondary': 'border border-paper-muted text-paper-foreground hover:bg-paper-foreground/10',
  'paper-ghost': 'text-paper-muted hover:bg-paper-foreground/10 hover:text-paper-foreground',
};

/**
 * The look of every button and button-like link. A directive on the native element
 * rather than a wrapper component, so the element keeps its own semantics (type, disabled,
 * routerLink, form submission) and its accessibility for free.
 */
@Directive({
  selector: 'button[appButton], a[appButton]',
  host: { '[class]': 'classes()' },
})
export class Button {
  readonly variant = input<ButtonVariant>('primary');
  readonly size = input<ButtonSize>('md');
  /** Full width, for a single action at the bottom of a form or a mobile panel. Written
   *  as a bare attribute (`<button appButton block>`), hence the boolean transform. */
  readonly block = input(false, { transform: booleanAttribute });

  protected readonly classes = computed(() =>
    [BASE, SIZES[this.size()], VARIANTS[this.variant()], this.block() ? 'w-full' : ''].join(' '),
  );
}
