import { TestBed } from '@angular/core/testing';
import { LIGHT_SCHEME_QUERY, LightSchemeQuery, Theme } from './theme';

/** A device color scheme the test can flip, like the OS switching to light. */
class FakeQuery implements LightSchemeQuery {
  private listener: ((event: { matches: boolean }) => void) | null = null;
  constructor(public matches: boolean) {}
  addEventListener(_: 'change', listener: (event: { matches: boolean }) => void): void {
    this.listener = listener;
  }
  removeEventListener(): void {
    this.listener = null;
  }
  flip(matches: boolean): void {
    this.matches = matches;
    this.listener?.({ matches });
  }
}

describe('Theme', () => {
  let query: FakeQuery;

  function create(systemLight: boolean): Theme {
    query = new FakeQuery(systemLight);
    TestBed.configureTestingModule({
      providers: [{ provide: LIGHT_SCHEME_QUERY, useValue: query }],
    });
    return TestBed.inject(Theme);
  }

  const isLight = () => document.documentElement.classList.contains('light');

  beforeEach(() => {
    window.localStorage.clear();
    document.documentElement.classList.remove('light');
  });

  it('follows a dark device when nothing is stored', () => {
    const service = create(false);

    expect(service.preference()).toBe('system');
    expect(service.theme()).toBe('dark');
    expect(isLight()).toBe(false);
  });

  it('follows a light device when nothing is stored, and its later changes', () => {
    const service = create(true);

    expect(service.theme()).toBe('light');
    expect(isLight()).toBe(true);

    query.flip(false);

    expect(service.theme()).toBe('dark');
    expect(isLight()).toBe(false);
  });

  it('is dark where the device scheme cannot be read', () => {
    TestBed.configureTestingModule({
      providers: [{ provide: LIGHT_SCHEME_QUERY, useValue: null }],
    });

    expect(TestBed.inject(Theme).theme()).toBe('dark');
  });

  it('keeps a stored choice whatever the device says', () => {
    window.localStorage.setItem('ageguessr-theme', 'dark');
    const service = create(true);

    expect(service.preference()).toBe('dark');
    expect(service.theme()).toBe('dark');

    query.flip(true);
    expect(isLight()).toBe(false);
  });

  it('remembers an explicit choice, and forgets it when going back to system', () => {
    const service = create(false);

    service.setPreference('light');

    expect(service.theme()).toBe('light');
    expect(isLight()).toBe(true);
    expect(window.localStorage.getItem('ageguessr-theme')).toBe('light');

    service.setPreference('system');

    expect(service.theme()).toBe('dark');
    expect(isLight()).toBe(false);
    expect(window.localStorage.getItem('ageguessr-theme')).toBeNull();
  });

  it('toggles to the other theme as an explicit choice', () => {
    const service = create(true);

    service.toggle();

    expect(service.preference()).toBe('dark');
    expect(window.localStorage.getItem('ageguessr-theme')).toBe('dark');
  });
});
