import { Component, Directive, computed, inject, input } from '@angular/core';

let nextId = 0;

/**
 * A labelled form field: label, the control (projected), an optional help text and an
 * error. It owns the ids, and the control inside reads them (`appInput`, `AgeInput`), so
 * the label, the help and the error are always tied to the control for screen readers.
 */
@Component({
  selector: 'app-field',
  host: { class: 'block' },
  template: `
    <label [for]="id()" class="mb-1.5 block text-sm font-semibold">{{ label() }}</label>
    <ng-content />
    @if (hint()) {
      <p [id]="hintId()" class="text-muted mt-1.5 text-[13px]">{{ hint() }}</p>
    }
    <!-- Always in the page, so screen readers announce an error as soon as it appears. -->
    <p [id]="errorId()" class="text-danger mt-1.5 text-[13px] empty:hidden" aria-live="polite">
      @if (error()) {
        {{ error() }}
      }
    </p>
  `,
})
export class Field {
  readonly label = input.required<string>();
  readonly hint = input<string | null>(null);
  /** The message to show, or null while the value is valid (or not yet touched). */
  readonly error = input<string | null>(null);

  /** A fixed id for the control (`email`), for a page whose links or tests target it.
   *  Without one, each field gets its own generated id. */
  readonly controlId = input<string | null>(null);

  private readonly generatedId = `field-${nextId++}`;
  readonly id = computed(() => this.controlId() ?? this.generatedId);
  protected readonly hintId = computed(() => `${this.id()}-hint`);
  protected readonly errorId = computed(() => `${this.id()}-error`);

  readonly describedBy = computed(
    () =>
      [this.hint() ? this.hintId() : null, this.error() ? this.errorId() : null]
        .filter((id) => id !== null)
        .join(' ') || null,
  );
}

/** The look of a text control, tied to the `app-field` around it. */
@Directive({
  selector: 'input[appInput], select[appInput], textarea[appInput]',
  host: {
    class:
      'w-full rounded-sm border border-input-border bg-background px-3 text-foreground ' +
      '[&:not(textarea)]:h-11.5 [&:is(textarea)]:py-2 ' +
      'focus:border-accent focus:ring-3 focus:ring-accent/30 focus:outline-none ' +
      'aria-invalid:border-danger disabled:opacity-50',
    '[attr.id]': 'field?.id()',
    '[attr.aria-describedby]': 'field?.describedBy()',
    '[attr.aria-invalid]': 'field?.error() ? "true" : null',
  },
})
export class TextInput {
  protected readonly field = inject(Field, { optional: true });
}
