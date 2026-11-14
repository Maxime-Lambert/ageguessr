import { Component, DestroyRef, computed, inject, input, output } from '@angular/core';
import { LucideCheck, LucideInfo } from '@lucide/angular';
import { CATEGORY_LABELS } from '../game-vocabulary';
import { leadHelp, leadHelpLabel } from '../lead-help-texts';
import { LeadIcon } from '../lead-icon/lead-icon';
import { HintCategory } from '../models';

/** The mouse rests this long on ⓘ before the help opens: brushing past it opens none. */
export const HOVER_DELAY_MS = 200;

/**
 * A lead to pick, with the help that tells what kind of fact it gives. The card itself is a
 * toggle (aria-pressed); the ⓘ button cannot sit inside it (no button in a button), so it
 * lies over its corner, a sibling in the same relative box. Only ⓘ opens the help (players
 * found it popping up over the cards in the way, 2026-11-14): a rest of the mouse on it, a
 * tap, a click, or Enter once Tab reached it. The mouse leaving the card, help included,
 * closes it. The round keeps which help is open (one at a time), so `open` comes in and the
 * wishes go out.
 */
@Component({
  selector: 'app-lead-card',
  imports: [LeadIcon, LucideCheck, LucideInfo],
  host: {
    class: 'relative block',
    '[attr.data-lead]': 'category()',
    '(pointerleave)': 'pointerLeft($event)',
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
      (click)="infoClicked($event)"
      (pointerenter)="pointerEntered($event)"
    >
      <svg lucideInfo [size]="18" aria-hidden="true"></svg>
    </button>
    <!-- Always in the page so the card can point to it (aria-describedby), shown when open.
         The top padding, not a margin, bridges the gap: the mouse goes down from ⓘ to it
         without leaving the card's box, which would close it. -->
    <div
      [id]="helpId()"
      class="absolute top-full z-10 w-[min(18rem,calc(100vw-2rem))] pt-2"
      [class]="alignment()"
      [hidden]="!open()"
    >
      <div class="card border-accent space-y-1.5 p-3.5 text-sm shadow-lg">
        <p class="font-semibold">{{ help().kind }}</p>
        <p>{{ help().rule }}</p>
        <p class="text-muted italic">{{ help().example }}</p>
      </div>
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
  protected readonly help = computed(() => leadHelp(this.category(), this.gameYear()));
  protected readonly helpId = computed(() => `lead-help-${this.category().toLowerCase()}`);
  // Whole class names, so that Tailwind finds them in the source.
  protected readonly alignment = computed(
    () =>
      `${this.rightOnMobile() ? 'max-sm:right-0' : 'max-sm:left-0'} ` +
      `${this.rightOnWide() ? 'sm:right-0' : 'sm:left-0'}`,
  );

  private hoverTimer: ReturnType<typeof setTimeout> | undefined;

  constructor() {
    inject(DestroyRef).onDestroy(() => clearTimeout(this.hoverTimer));
  }

  protected pointerEntered(event: PointerEvent): void {
    if (event.pointerType !== 'mouse') return;
    clearTimeout(this.hoverTimer);
    this.hoverTimer = setTimeout(() => this.helpOpened.emit(), HOVER_DELAY_MS);
  }

  /** Leaving the whole card (ⓘ and the help are inside it), not ⓘ alone, closes it. */
  protected pointerLeft(event: PointerEvent): void {
    if (event.pointerType !== 'mouse') return;
    clearTimeout(this.hoverTimer);
    if (this.open()) this.helpClosed.emit();
  }

  /**
   * A tap or a key toggles the help. A mouse click only opens it: the rest on ⓘ may have
   * opened it just before, and the click must not close what it came for.
   */
  protected infoClicked(event: MouseEvent): void {
    clearTimeout(this.hoverTimer);
    const mouse = event instanceof PointerEvent && event.pointerType === 'mouse';
    if (this.open() && !mouse) {
      this.helpClosed.emit();
    } else if (!this.open()) {
      this.helpOpened.emit();
    }
  }

  /** The keyboard leaves the card (and its ⓘ): its help closes. */
  protected focusLeft(event: FocusEvent): void {
    const host = event.currentTarget as HTMLElement;
    if (this.open() && !host.contains(event.relatedTarget as Node | null)) {
      this.helpClosed.emit();
    }
  }
}
