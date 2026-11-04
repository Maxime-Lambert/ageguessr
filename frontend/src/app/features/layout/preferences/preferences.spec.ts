import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { HintLanguage } from '../../game/hint-language';
import { LIGHT_SCHEME_QUERY, Theme } from '../../theme/theme';
import { Preferences } from './preferences';

describe('Preferences', () => {
  beforeEach(() => {
    localStorage.clear();
    document.documentElement.classList.remove('light');
  });

  function render() {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: LIGHT_SCHEME_QUERY, useValue: null },
      ],
    });
    const fixture = TestBed.createComponent(Preferences);
    fixture.detectChanges();
    const page = fixture.nativeElement as HTMLElement;
    const group = (name: string) =>
      page.querySelector(`[role="radiogroup"][aria-label="${name}"]`)!;
    const option = (name: string, label: string) =>
      Array.from(group(name).querySelectorAll<HTMLButtonElement>('[role="radio"]')).find(
        (radio) => radio.textContent?.trim() === label,
      )!;
    return { fixture, page, option };
  }

  it('offers the other site language as a link', () => {
    const { page } = render();

    expect(page.textContent).toContain('Site language');
    expect(page.querySelector('a[hreflang="fr"]')?.textContent?.trim()).toBe('Français');
  });

  it('changes the language of the hints', () => {
    const { fixture, option } = render();

    option('Hints in', 'Français').click();
    fixture.detectChanges();

    expect(TestBed.inject(HintLanguage).value()).toBe('FR');
    expect(option('Hints in', 'Français').getAttribute('aria-checked')).toBe('true');
    expect(option('Hints in', 'Français').getAttribute('lang')).toBe('fr');
  });

  it('follows the device by default, and remembers a chosen theme', () => {
    const { fixture, option } = render();

    expect(option('Theme', 'Auto').getAttribute('aria-checked')).toBe('true');

    option('Theme', 'Light').click();
    fixture.detectChanges();

    expect(TestBed.inject(Theme).preference()).toBe('light');
    expect(document.documentElement.classList.contains('light')).toBe(true);
    expect(option('Theme', 'Light').getAttribute('aria-checked')).toBe('true');
  });
});
