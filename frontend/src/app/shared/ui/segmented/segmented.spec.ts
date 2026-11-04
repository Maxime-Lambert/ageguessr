import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Segmented, SegmentedOption } from './segmented';

type Scheme = 'dark' | 'light' | 'system';

@Component({
  imports: [Segmented],
  template: `<app-segmented label="Theme" [options]="options" [(value)]="scheme" />`,
})
class Host {
  readonly options: SegmentedOption<Scheme>[] = [
    { value: 'dark', label: 'Dark' },
    { value: 'light', label: 'Light' },
    { value: 'system', label: 'Auto' },
  ];
  readonly scheme = signal<Scheme>('dark');
}

describe('Segmented', () => {
  function render() {
    const fixture = TestBed.createComponent(Host);
    fixture.detectChanges();
    const page = fixture.nativeElement as HTMLElement;
    const radios = Array.from(page.querySelectorAll<HTMLButtonElement>('[role="radio"]'));
    return { fixture, page, radios };
  }

  const checked = (radios: HTMLButtonElement[]) =>
    radios.map((radio) => radio.getAttribute('aria-checked'));

  it('is a named radio group with the selected option checked and focusable', () => {
    const { page, radios } = render();

    expect(page.querySelector('[role="radiogroup"]')!.getAttribute('aria-label')).toBe('Theme');
    expect(checked(radios)).toEqual(['true', 'false', 'false']);
    expect(radios.map((radio) => radio.tabIndex)).toEqual([0, -1, -1]);
  });

  it('selects an option on click', () => {
    const { fixture, radios } = render();

    radios[2].click();
    fixture.detectChanges();

    expect(fixture.componentInstance.scheme()).toBe('system');
    expect(checked(radios)).toEqual(['false', 'false', 'true']);
  });

  it('moves and selects with the arrow keys, wrapping around', () => {
    const { fixture, radios } = render();

    radios[0].dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowLeft' }));
    fixture.detectChanges();

    expect(fixture.componentInstance.scheme()).toBe('system');
    expect(document.activeElement).toBe(radios[2]);

    radios[2].dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight' }));
    fixture.detectChanges();
    expect(fixture.componentInstance.scheme()).toBe('dark');
  });

  it('jumps to the last and first options with End and Home', () => {
    const { fixture, radios } = render();

    radios[0].dispatchEvent(new KeyboardEvent('keydown', { key: 'End' }));
    fixture.detectChanges();
    expect(fixture.componentInstance.scheme()).toBe('system');
    expect(document.activeElement).toBe(radios[2]);

    radios[2].dispatchEvent(new KeyboardEvent('keydown', { key: 'Home' }));
    fixture.detectChanges();
    expect(fixture.componentInstance.scheme()).toBe('dark');
  });
});
