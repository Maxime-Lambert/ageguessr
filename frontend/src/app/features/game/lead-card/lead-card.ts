import { Component, computed, input, output } from '@angular/core';
import { LucideCheck, LucideInfo } from '@lucide/angular';
import { CATEGORY_LABELS } from '../game-vocabulary';
import { LeadHelpPanel } from '../lead-help/lead-help-panel';
import { leadHelpLabel } from '../lead-help-texts';
import { LeadIcon } from '../lead-icon/lead-icon';
import { HintCategory } from '../models';

/**
 * A lead to pick, with the help that tells what kind of fact it gives. The card itself is a
 * toggle (aria-pressed); the ⓘ button cannot sit inside it (no button in a button), so it
 * lies over its corner, a sibling in the same relative box. Only ⓘ opens the help, and
 * only on purpose: a click, a tap, or Enter once Tab reached it, each toggling it. The
 * mouse passing over opened it once (2026-11-14), testers found it popping up in the way
 * and lingering (2026-11-23). The round keeps which help is open (one at a time) and
 * closes it on a click elsewhere or Escape, so `open` comes in and the wishes go out.
 */
@Component({
  selector: 'app-lead-card',
  imports: [LeadIcon, LeadHelpPanel, LucideCheck, LucideInfo],
  host: {
    class: 'relative block',
    '[attr.data-lead]': 'category()',
    '(focusout)': 'focusLeft($event)',
  },
  template: `
    <button
      #card
      type="button"
      class="card hover:border-accent aria-pressed:border-accent aria-pressed:bg-surface-2 flex h-full w-full flex-col items-start gap-2.5 p-4 pr-11 text-left text-[15px] font-semibold disabled:opacity-50"
      [attr.aria-pressed]="picked()"
      [attr.aria-describedby]="helpId()"
      [disabled]="disabled()"
      (click)="toggled.emit()"
    >
      <span class="flex w-full items-center justify-between">
        <app-lead-icon class="text-accent" [category]="category()" />
        @if (picked()) {
          <svg lucideCheck class="text-accent" [size]="18" aria-hidden="true"></svg>
        }
      </span>
      {{ label() }}
    </button>
    <!-- Outside the card, so it stays usable when the card is disabled (3 leads picked). -->
    <button
      type="button"
      class="text-muted hover:text-accent focus-visible:text-accent absolute right-0.5 bottom-0.5 grid size-10 place-items-center rounded-md"
      [attr.aria-label]="buttonLabel()"
      [attr.aria-expanded]="open()"
      [attr.aria-controls]="helpId()"
      (click)="open() ? helpClosed.emit() : helpOpened.emit()"
    >
      <svg lucideInfo [size]="18" aria-hidden="true"></svg>
    </button>
    <!-- Always in the page so the card can point to it (aria-describedby), shown when open. -->
    <div
      [id]="helpId()"
      class="absolute top-full z-10 w-[min(18rem,calc(100vw-2rem))] pt-2"
      [class]="alignment()"
      [hidden]="!open()"
    >
      <app-lead-help-panel [category]="category()" [gameYear]="gameYear()" />
    </div>
  `,
})
export class LeadCard {
  readonly category = input.required<HintCategory>();
  readonly gameYear = input.required<number>();
  readonly picked = input(false);
  readonly disabled = input(false);
  readonly open = input(false);
  /** Aligned on the card's right edge in the last column, so it stays on the screen. */
  readonly rightOnMobile = input(false);
  readonly rightOnWide = input(false);

  readonly toggled = output<void>();
  readonly helpOpened = output<void>();
  readonly helpClosed = output<void>();

  protected readonly label = computed(() => CATEGORY_LABELS[this.category()]);
  protected readonly buttonLabel = computed(() => leadHelpLabel(this.label()));
  protected readonly helpId = computed(() => `lead-help-${this.category().toLowerCase()}`);
  // Whole class names, so that Tailwind finds them in the source.
  protected readonly alignment = computed(
    () =>
      `${this.rightOnMobile() ? 'max-sm:right-0' : 'max-sm:left-0'} ` +
      `${this.rightOnWide() ? 'sm:right-0' : 'sm:left-0'}`,
  );

  /** The keyboard leaves the card (and its ⓘ): its help closes. */
  protected focusLeft(event: FocusEvent): void {
    const host = event.currentTarget as HTMLElement;
    if (this.open() && !host.contains(event.relatedTarget as Node | null)) {
      this.helpClosed.emit();
    }
  }
}
