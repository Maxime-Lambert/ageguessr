import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { LanguageSwitch } from './language-switch';

describe('LanguageSwitch', () => {
  let fixture: ComponentFixture<LanguageSwitch>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [LanguageSwitch],
      providers: [provideRouter([])],
    }).compileComponents();
    fixture = TestBed.createComponent(LanguageSwitch);
  });

  function link(): HTMLAnchorElement {
    fixture.detectChanges();
    return (fixture.nativeElement as HTMLElement).querySelector('a')!;
  }

  it('offers the French version from the English one, labelled in French', () => {
    fixture.componentRef.setInput('language', 'en');

    expect(link().textContent?.trim()).toBe('Version française');
    expect(link().getAttribute('lang')).toBe('fr');
    expect(link().getAttribute('href')).toContain('lang=fr');
  });

  it('offers the English version from the French one, without any lang parameter', () => {
    fixture.componentRef.setInput('language', 'fr');

    expect(link().textContent?.trim()).toBe('English version');
    expect(link().getAttribute('lang')).toBe('en');
    expect(link().getAttribute('href')).not.toContain('lang=');
  });
});
