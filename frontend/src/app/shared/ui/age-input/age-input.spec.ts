import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { Field } from '../field/field';
import { AgeInput } from './age-input';

@Component({
  imports: [AgeInput, Field, ReactiveFormsModule],
  template: `
    <app-field
      label="Your guess"
      [error]="guess.invalid && guess.touched ? 'Between 18 and 100' : null"
    >
      <app-age-input [formControl]="guess" />
    </app-field>
  `,
})
class Host {
  readonly guess = new FormControl<number | null>(null);
}

describe('AgeInput', () => {
  function render() {
    const fixture = TestBed.createComponent(Host);
    fixture.detectChanges();
    const page = fixture.nativeElement as HTMLElement;
    const [less, more] = Array.from(page.querySelectorAll('button'));
    const control = page.querySelector('input')!;
    return { fixture, guess: fixture.componentInstance.guess, page, less, more, control };
  }

  function type(control: HTMLInputElement, text: string) {
    control.value = text;
    control.dispatchEvent(new Event('input'));
  }

  function key(control: HTMLInputElement, name: string) {
    control.dispatchEvent(new KeyboardEvent('keydown', { key: name }));
  }

  it('is labelled by its field and exposed as a spinbutton', () => {
    const { page, control } = render();

    expect(page.querySelector('label')!.getAttribute('for')).toBe(control.id);
    expect(control.getAttribute('role')).toBe('spinbutton');
    expect(control.getAttribute('inputmode')).toBe('numeric');
    expect(control.getAttribute('aria-valuemin')).toBe('18');
    expect(control.getAttribute('aria-valuemax')).toBe('100');
  });

  it('keeps only digits from what is typed', () => {
    const { guess, control } = render();

    type(control, '4a2');

    expect(control.value).toBe('42');
    expect(guess.value).toBe(42);

    type(control, '');
    expect(guess.value).toBeNull();
  });

  it('starts the buttons from a typical age, then steps by one within the range', () => {
    const { fixture, guess, less, more } = render();

    more.click();
    expect(guess.value).toBe(35);
    more.click();
    expect(guess.value).toBe(36);

    guess.setValue(18);
    fixture.detectChanges();
    expect(less.disabled).toBe(true);

    guess.setValue(100);
    fixture.detectChanges();
    more.click();
    expect(guess.value).toBe(100);
  });

  it('steps with the arrow keys', () => {
    const { fixture, guess, control } = render();
    guess.setValue(40);
    fixture.detectChanges();

    key(control, 'ArrowUp');
    expect(guess.value).toBe(41);
    key(control, 'ArrowDown');
    key(control, 'ArrowDown');
    expect(guess.value).toBe(39);
    fixture.detectChanges();
    expect(control.getAttribute('aria-valuenow')).toBe('39');
    expect(control.getAttribute('aria-valuetext')).toBe('39 years');
  });

  it('shows a value set by the form, and is disabled with it', () => {
    const { fixture, guess, control, more } = render();

    guess.setValue(57);
    guess.disable();
    fixture.detectChanges();

    expect(control.value).toBe('57');
    expect(control.disabled).toBe(true);
    expect(more.disabled).toBe(true);
  });

  it('marks the form control touched on blur', () => {
    const { guess, control } = render();

    control.dispatchEvent(new Event('blur'));

    expect(guess.touched).toBe(true);
  });
});
