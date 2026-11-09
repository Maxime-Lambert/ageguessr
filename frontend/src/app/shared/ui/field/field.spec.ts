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
    <app-field label="Password" controlId="password" hint="12 characters">
      <input appInput type="password" />
    </app-field>
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
    const [first, second, third] = Array.from(page.querySelectorAll('app-field'));
    return { fixture, first, second, third };
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

  it('uses a fixed id when given one, for its label and help text too', () => {
    const { third } = render();
    const control = third.querySelector('input')!;

    expect(control.id).toBe('password');
    expect(third.querySelector('label')!.getAttribute('for')).toBe('password');
    expect(control.getAttribute('aria-describedby')).toBe('password-hint');
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
