import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { GAME_TEXT } from '../game-vocabulary';
import { HintLanguage } from '../hint-language';
import { HintLanguagePicker } from './hint-language-picker';

describe('HintLanguagePicker', () => {
  beforeEach(() => localStorage.clear());

  it('shows the language of the hints as pressed, and changes it', () => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    const fixture = TestBed.createComponent(HintLanguagePicker);
    fixture.detectChanges();
    const page = fixture.nativeElement as HTMLElement;
    const [english, french] = Array.from(page.querySelectorAll('button'));

    expect(page.textContent).toContain(GAME_TEXT.hintLanguage);
    expect(english.getAttribute('aria-pressed')).toBe('true');
    expect(french.getAttribute('aria-pressed')).toBe('false');

    french.click();
    fixture.detectChanges();

    expect(TestBed.inject(HintLanguage).value()).toBe('FR');
    expect(french.getAttribute('aria-pressed')).toBe('true');
    expect(french.getAttribute('lang')).toBe('fr');
  });
});
