import { TestBed } from '@angular/core/testing';
import { Theme } from './theme';

describe('Theme', () => {
  beforeEach(() => {
    window.localStorage.clear();
    document.documentElement.classList.remove('light');
    TestBed.configureTestingModule({});
  });

  it('defaults to dark when nothing is stored', () => {
    const service = TestBed.inject(Theme);

    expect(service.theme()).toBe('dark');
    expect(document.documentElement.classList.contains('light')).toBe(false);
  });

  it('reads a previously stored theme', () => {
    window.localStorage.setItem('ageguessr-theme', 'light');

    const service = TestBed.inject(Theme);

    expect(service.theme()).toBe('light');
    expect(document.documentElement.classList.contains('light')).toBe(true);
  });

  it('toggles between dark and light, persisting the choice', () => {
    const service = TestBed.inject(Theme);

    service.toggle();

    expect(service.theme()).toBe('light');
    expect(document.documentElement.classList.contains('light')).toBe(true);
    expect(window.localStorage.getItem('ageguessr-theme')).toBe('light');

    service.toggle();

    expect(service.theme()).toBe('dark');
    expect(document.documentElement.classList.contains('light')).toBe(false);
    expect(window.localStorage.getItem('ageguessr-theme')).toBe('dark');
  });
});
