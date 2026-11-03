import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { LOCALE_ID } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { HintLanguage } from './hint-language';

describe('HintLanguage', () => {
  function create(locale: string): HintLanguage {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: LOCALE_ID, useValue: locale },
      ],
    });
    return TestBed.inject(HintLanguage);
  }

  beforeEach(() => localStorage.clear());

  it('follows the language of the site until the player picks one', () => {
    expect(create('fr').value()).toBe('FR');
  });

  it('keeps the choice of the player on this device', () => {
    create('fr').set('EN');

    expect(localStorage.getItem('ageguessr-hint-language')).toBe('EN');
  });

  it('starts from the choice made earlier, whatever the language of the site', () => {
    localStorage.setItem('ageguessr-hint-language', 'EN');

    expect(create('fr').value()).toBe('EN');
  });

  it('ignores an unknown stored value', () => {
    localStorage.setItem('ageguessr-hint-language', 'DE');

    expect(create('en').value()).toBe('EN');
  });
});
