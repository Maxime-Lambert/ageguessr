import { Component, computed, input } from '@angular/core';

/**
 * Where the player is in the file, as one bar per case: done, current, to come.
 * Decorative: the heading next to it already says « Case 1 of 3 ». Shorter bars for a
 * long series, so 20 cases still fit on a phone.
 */
@Component({
  selector: 'app-case-steps',
  host: { class: 'inline-flex flex-wrap gap-1.5', 'aria-hidden': 'true' },
  template: `
    @for (step of steps(); track $index) {
      <i
        class="block h-1 rounded-sm"
        [class.w-7]="total() <= 5"
        [class.w-3]="total() > 5"
        [class.bg-foreground]="step === 'done'"
        [class.bg-accent]="step === 'now'"
        [class.bg-border]="step === 'next'"
      ></i>
    }
  `,
})
export class CaseSteps {
  readonly position = input.required<number>();
  readonly total = input.required<number>();

  protected readonly steps = computed(() =>
    Array.from({ length: this.total() }, (_, i) =>
      i + 1 < this.position() ? 'done' : i + 1 === this.position() ? 'now' : 'next',
    ),
  );
}
