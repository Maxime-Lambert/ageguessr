import { Component, ElementRef, input, model, viewChildren } from '@angular/core';

export interface SegmentedOption<T> {
  readonly value: T;
  readonly label: string;
  /** Set when the label is in another language than the page (« English » on /fr/). */
  readonly lang?: string;
}

/**
 * A short exclusive choice shown in full (FR / EN, dark / light / auto): a radio group,
 * with the keyboard behaviour screen-reader users expect from one. Tab enters on the
 * selected option, the arrow keys move and select, Home and End jump to the ends.
 */
@Component({
  selector: 'app-segmented',
  host: { class: 'inline-flex' },
  template: `
    <div
      role="radiogroup"
      class="border-border inline-flex rounded-sm border p-0.5"
      [attr.aria-label]="label()"
    >
      @for (option of options(); track option.value; let i = $index) {
        <button
          #option
          type="button"
          role="radio"
          class="text-muted hover:text-foreground aria-checked:bg-surface-2 aria-checked:text-foreground rounded-[3px] px-2.5 py-1 text-[13px] aria-checked:font-semibold"
          [attr.aria-checked]="option.value === value()"
          [attr.lang]="option.lang ?? null"
          [tabIndex]="option.value === value() ? 0 : -1"
          (click)="value.set(option.value)"
          (keydown.arrowright)="move(i, 1, $event)"
          (keydown.arrowdown)="move(i, 1, $event)"
          (keydown.arrowleft)="move(i, -1, $event)"
          (keydown.arrowup)="move(i, -1, $event)"
          (keydown.home)="select(0, $event)"
          (keydown.end)="select(options().length - 1, $event)"
        >
          {{ option.label }}
        </button>
      }
    </div>
  `,
})
export class Segmented<T> {
  /** The accessible name of the group (« Theme »), read before the options. */
  readonly label = input.required<string>();
  readonly options = input.required<readonly SegmentedOption<T>[]>();
  readonly value = model.required<T>();

  private readonly buttons = viewChildren<ElementRef<HTMLButtonElement>>('option');

  protected move(index: number, delta: number, event: Event): void {
    const count = this.options().length;
    this.select((index + delta + count) % count, event);
  }

  protected select(index: number, event: Event): void {
    event.preventDefault();
    this.value.set(this.options()[index].value);
    this.buttons()[index].nativeElement.focus();
  }
}
