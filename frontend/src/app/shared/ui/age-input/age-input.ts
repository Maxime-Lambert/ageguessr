import { Component, ElementRef, forwardRef, inject, input, signal, viewChild } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import { Field } from '../field/field';

/**
 * The age guess: large digits between a « − » and a « + » button. A text field with a
 * numeric keyboard rather than `type="number"`, whose native arrows are tiny and whose
 * value a mouse wheel can change by accident. The arrow keys step by one, as a spinbutton.
 * Out-of-range values are left to the form's validators: this control only refuses what
 * is not digits.
 */
@Component({
  selector: 'app-age-input',
  host: { class: 'block' },
  providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => AgeInput), multi: true }],
  template: `
    <div
      class="border-input-border bg-background focus-within:border-accent focus-within:ring-accent/30 flex h-18 max-w-80 items-stretch rounded-md border-[1.5px] focus-within:ring-3"
    >
      <button
        type="button"
        tabindex="-1"
        class="text-muted hover:text-foreground grid w-16 place-items-center text-2xl disabled:opacity-40"
        [attr.aria-label]="lessLabel"
        [disabled]="disabled() || value() === min()"
        (click)="step(-1)"
      >
        −
      </button>
      <!-- The digits are narrow: a click anywhere between the buttons, "years" included,
           puts the caret in the field. A pointer-only shortcut, the field itself stays the
           keyboard's and screen readers' target. -->
      <div
        class="border-border flex flex-1 cursor-text items-baseline justify-center gap-1.5 border-x"
        (mousedown)="focusFrom($event)"
      >
        <input
          #control
          type="text"
          inputmode="numeric"
          autocomplete="off"
          maxlength="3"
          placeholder="?"
          role="spinbutton"
          class="font-display placeholder:text-muted w-[2.2ch] bg-transparent text-center text-[40px] leading-[69px] font-semibold outline-none"
          [attr.id]="field?.id()"
          [attr.aria-describedby]="field?.describedBy()"
          [attr.aria-invalid]="field?.error() ? 'true' : null"
          [attr.aria-valuemin]="min()"
          [attr.aria-valuemax]="max()"
          [attr.aria-valuenow]="value()"
          [attr.aria-valuetext]="value() === null ? null : value() + ' ' + unit"
          [value]="value() ?? ''"
          [disabled]="disabled()"
          (input)="typed(control.value)"
          (keydown.arrowup)="$event.preventDefault(); step(1)"
          (keydown.arrowdown)="$event.preventDefault(); step(-1)"
          (blur)="onTouched()"
        />
        <span class="text-muted text-[15px]" aria-hidden="true">{{ unit }}</span>
      </div>
      <button
        type="button"
        tabindex="-1"
        class="text-muted hover:text-foreground grid w-16 place-items-center text-2xl disabled:opacity-40"
        [attr.aria-label]="moreLabel"
        [disabled]="disabled() || value() === max()"
        (click)="step(1)"
      >
        +
      </button>
    </div>
  `,
})
export class AgeInput implements ControlValueAccessor {
  readonly min = input(18);
  readonly max = input(100);
  /** Where the buttons start from while the field is empty: a typical adult age. */
  readonly start = input(35);

  protected readonly field = inject(Field, { optional: true });
  protected readonly value = signal<number | null>(null);
  protected readonly disabled = signal(false);
  private readonly control = viewChild.required<ElementRef<HTMLInputElement>>('control');

  protected readonly unit = $localize`:@@ui.ageInput.unit:years`;
  protected readonly lessLabel = $localize`:@@ui.ageInput.less:One year younger`;
  protected readonly moreLabel = $localize`:@@ui.ageInput.more:One year older`;

  // No-ops until the form registers its callbacks (a control used without a form).
  private onChange: (value: number | null) => void = () => undefined;
  protected onTouched: () => void = () => undefined;

  focus(): void {
    this.control().nativeElement.focus();
  }

  /** Outside the field itself: no blur in between, and the caret goes after the digits. */
  protected focusFrom(event: MouseEvent): void {
    const input = this.control().nativeElement;
    if (event.target === input || this.disabled()) return;
    event.preventDefault();
    input.focus();
    input.setSelectionRange(input.value.length, input.value.length);
  }

  protected typed(text: string): void {
    const digits = text.replace(/\D/g, '');
    // Write back the cleaned text, so a letter typed never stays on screen.
    this.control().nativeElement.value = digits;
    this.update(digits === '' ? null : Number(digits));
  }

  protected step(delta: number): void {
    if (this.disabled()) return;
    const current = this.value();
    const next = current === null ? this.start() : current + delta;
    this.update(Math.min(this.max(), Math.max(this.min(), next)));
    this.onTouched();
  }

  private update(value: number | null): void {
    this.value.set(value);
    this.onChange(value);
  }

  writeValue(value: number | null): void {
    this.value.set(value ?? null);
  }

  registerOnChange(fn: (value: number | null) => void): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }

  setDisabledState(disabled: boolean): void {
    this.disabled.set(disabled);
  }
}
