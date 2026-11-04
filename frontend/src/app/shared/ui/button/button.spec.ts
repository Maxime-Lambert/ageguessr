import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Button, ButtonSize, ButtonVariant } from './button';

@Component({
  imports: [Button],
  template: `
    <button
      appButton
      type="button"
      class="mt-4"
      [variant]="variant()"
      [size]="size()"
      [block]="block()"
    >
      Go
    </button>
    <a appButton variant="secondary" href="/free">Free</a>
  `,
})
class Host {
  readonly variant = signal<ButtonVariant>('primary');
  readonly size = signal<ButtonSize>('md');
  readonly block = signal(false);
}

describe('Button', () => {
  function render() {
    const fixture = TestBed.createComponent(Host);
    fixture.detectChanges();
    const page = fixture.nativeElement as HTMLElement;
    return { fixture, button: page.querySelector('button')!, link: page.querySelector('a')! };
  }

  it('is a primary, medium button by default, keeping its own classes', () => {
    const { button } = render();

    expect(button.className).toContain('bg-primary');
    expect(button.className).toContain('h-11');
    expect(button.className).toContain('mt-4');
    expect(button.className).not.toContain('w-full');
  });

  it('follows its variant, size and width', () => {
    const { fixture, button } = render();

    fixture.componentInstance.variant.set('danger');
    fixture.componentInstance.size.set('sm');
    fixture.componentInstance.block.set(true);
    fixture.detectChanges();

    expect(button.className).toContain('bg-danger');
    expect(button.className).not.toContain('bg-primary');
    expect(button.className).toContain('h-9');
    expect(button.className).toContain('w-full');
  });

  it('styles a link without turning it into a button', () => {
    const { link } = render();

    expect(link.className).toContain('border-input-border');
    expect(link.getAttribute('href')).toBe('/free');
    expect(link.getAttribute('role')).toBeNull();
  });
});
