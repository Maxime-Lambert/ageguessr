import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Footer } from './footer';

describe('Footer', () => {
  let fixture: ComponentFixture<Footer>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Footer],
      providers: [provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(Footer);
    fixture.detectChanges();
  });

  function link(text: string): HTMLAnchorElement | undefined {
    return Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('a')).find(
      (a) => a.textContent?.trim() === text,
    );
  }

  it('links to the three legal pages', () => {
    expect(link('Legal notice')?.getAttribute('href')).toBe('/legal-notice');
    expect(link('Terms')?.getAttribute('href')).toBe('/terms');
    expect(link('Privacy')?.getAttribute('href')).toBe('/privacy');
  });

  it('shows the contact address as a mailto link', () => {
    expect(link('contact@ageguessr.app')?.getAttribute('href')).toBe(
      'mailto:contact@ageguessr.app',
    );
  });
});
