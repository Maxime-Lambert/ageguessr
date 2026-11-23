import { Component, ElementRef, computed, inject, input, signal } from '@angular/core';
import { LucideInfo } from '@lucide/angular';
import { CATEGORY_LABELS } from '../game-vocabulary';
import { leadHelpLabel } from '../lead-help-texts';
import { HintCategory } from '../models';
import { LeadHelpPanel } from './lead-help-panel';

/**
 * The ⓘ of a hint already revealed, so that the player finds again what its category means
 * (a tester forgot it, 2026-11-23). Same rule as on the lead cards: a click, a tap or Enter
 * toggles it, never the mouse passing over; a click elsewhere, Escape or the focus leaving
 * closes it. Each one keeps its own state: several can sit on the screen, one hint each.
 */
@Component({
  selector: 'app-lead-help-button',
  imports: [LucideInfo, LeadHelpPanel],
  host: {
    class: 'relative inline-flex',
    '(document:pointerdown)': 'pointedAt($event)',
    '(document:keydown.escape)': 'open.set(false)',
    '(focusout)': 'focusLeft($event)',
  },
  template: `
    <button
      type="button"
      class="hover:text-accent focus-visible:text-accent -m-2 grid size-8 place-items-center rounded-md"
      [attr.aria-label]="label()"
      [attr.aria-expanded]="open()"
      [attr.aria-controls]="helpId()"
      (click)="open.set(!open())"
    >
      <svg lucideInfo [size]="14" aria-hidden="true"></svg>
    </button>
    <div
      [id]="helpId()"
      class="absolute top-full left-0 z-10 w-[min(18rem,calc(100vw-2rem))] pt-2 max-sm:-left-8"
      [hidden]="!open()"
    >
      <app-lead-help-panel [category]="category()" [gameYear]="gameYear()" />
    </div>
  `,
})
export class LeadHelpButton {
  readonly category = input.required<HintCategory>();
  readonly gameYear = input.required<number>();

  readonly open = signal(false);

  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;

  protected readonly label = computed(() => leadHelpLabel(CATEGORY_LABELS[this.category()]));
  protected readonly helpId = computed(() => `revealed-help-${this.category().toLowerCase()}`);

  protected pointedAt(event: PointerEvent): void {
    if (this.open() && !this.host.contains(event.target as Node | null)) this.open.set(false);
  }

  protected focusLeft(event: FocusEvent): void {
    if (this.open() && !this.host.contains(event.relatedTarget as Node | null)) {
      this.open.set(false);
    }
  }
}
