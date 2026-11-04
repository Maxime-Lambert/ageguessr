import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Field, TextInput } from './field';

@Component({
  imports: [Field, TextInput],
  template: `
    <app-field label="Username" [hint]="hint()" [error]="error()">
      <input appInput type="text" />
    </app-field>
    <app-field label="Email"><input appInput type="email" /></app-field>
  `,
})
class Host {
  readonly hint = signal<string | null>('Visible to your friends.');
  readonly error = signal<string | null>(null);
}

describe('Field', () => {
  function render() {
    const fixture = TestBed.createComponent(Host);
    fixture.detectChanges();
    const page = fixture.nativeElement as HTMLElement;
    const [first, second] = Array.from(page.querySelectorAll('app-field'));
    return { fixture, first, second };
  }

  it('ties the label and the help text to the control', () => {
    const { first } = render();
    const control = first.querySelector('input')!;
    const label = first.querySelector('label')!;

    expect(label.textContent).toContain('Username');
    expect(label.getAttribute('for')).toBe(control.id);
    const help = first.querySelector(`#${control.getAttribute('aria-describedby')}`);
    expect(help?.textContent).toContain('Visible to your friends.');
    expect(control.getAttribute('aria-invalid')).toBeNull();
  });

  it('gives each field its own ids', () => {
    const { first, second } = render();

    expect(first.querySelector('input')!.id).not.toBe(second.querySelector('input')!.id);
    expect(second.querySelector('input')!.getAttribute('aria-describedby')).toBeNull();
  });

  it('announces an error and marks the control invalid', () => {
    const { fixture, first } = render();

    fixture.componentInstance.error.set('Already taken.');
    fixture.detectChanges();
    const control = first.querySelector('input')!;
    const described = control.getAttribute('aria-describedby')!.split(' ');

    expect(control.getAttribute('aria-invalid')).toBe('true');
    expect(described).toHaveLength(2);
    const error = first.querySelector(`#${described[1]}`)!;
    expect(error.textContent).toContain('Already taken.');
    expect(error.getAttribute('aria-live')).toBe('polite');
  });

  it('styles a control used outside any field', () => {
    const fixture = TestBed.createComponent(Bare);
    fixture.detectChanges();
    const control = (fixture.nativeElement as HTMLElement).querySelector('input')!;

    expect(control.className).toContain('border-input-border');
    expect(control.hasAttribute('id')).toBe(false);
  });
});

@Component({ imports: [TextInput], template: `<input appInput aria-label="Search" />` })
class Bare {}
