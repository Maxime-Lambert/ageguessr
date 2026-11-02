import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { Component, LOCALE_ID } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { BROWSER_PAGE } from '../site-language';
import { fakePage } from '../testing';
import { LanguageSwitch } from './language-switch';

@Component({ template: '' })
class Blank {}

describe('LanguageSwitch', () => {
  async function render(locale: string) {
    const page = fakePage();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([{ path: '**', component: Blank }]),
        { provide: LOCALE_ID, useValue: locale },
        { provide: BROWSER_PAGE, useValue: page },
      ],
    });
    await TestBed.inject(Router).navigateByUrl('/privacy');
    const fixture = TestBed.createComponent(LanguageSwitch);
    fixture.detectChanges();
    return { page, link: (fixture.nativeElement as HTMLElement).querySelector('a')! };
  }

  it('offers the French version, named in French, from the English site', async () => {
    const { link } = await render('en');

    expect(link.textContent?.trim()).toBe('Français');
    expect(link.getAttribute('href')).toBe('/fr/privacy');
    expect(link.getAttribute('hreflang')).toBe('fr');
    expect(link.getAttribute('lang')).toBe('fr');
  });

  it('offers the English version from the French site', async () => {
    const { link } = await render('fr');

    expect(link.textContent?.trim()).toBe('English');
    expect(link.getAttribute('href')).toBe('/en/privacy');
  });

  it('switches on click', async () => {
    const { link, page } = await render('en');

    link.click();

    expect(page.loaded).toEqual(['/fr/privacy']);
  });
});
