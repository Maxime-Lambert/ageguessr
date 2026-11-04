import {
  DestroyRef,
  InjectionToken,
  Service,
  computed,
  effect,
  inject,
  signal,
} from '@angular/core';

export type ThemeName = 'dark' | 'light';
/** What the player chose: a theme, or « system », which follows the device. */
export type ThemePreference = ThemeName | 'system';

const STORAGE_KEY = 'ageguessr-theme';

/** The part of a MediaQueryList the theme reads, behind a token so the specs can fake it. */
export interface LightSchemeQuery {
  readonly matches: boolean;
  addEventListener(type: 'change', listener: (event: { matches: boolean }) => void): void;
  removeEventListener(type: 'change', listener: (event: { matches: boolean }) => void): void;
}

export const LIGHT_SCHEME_QUERY = new InjectionToken<LightSchemeQuery | null>(
  'LightSchemeQuery',
  // null where matchMedia does not exist (jsdom, very old browsers): « system » is dark.
  { factory: () => window.matchMedia?.('(prefers-color-scheme: light)') ?? null },
);

function readStoredPreference(): ThemePreference {
  try {
    const stored = window.localStorage.getItem(STORAGE_KEY);
    return stored === 'light' || stored === 'dark' ? stored : 'system';
  } catch {
    return 'system';
  }
}

/**
 * The theme of the site. Follows the device until the player picks dark or light, which
 * is then remembered on this device. index.html applies the same rule before Angular
 * starts, so the page never flashes the wrong theme.
 */
@Service()
export class Theme {
  private readonly query = inject(LIGHT_SCHEME_QUERY);
  private readonly systemLight = signal(this.query?.matches ?? false);

  readonly preference = signal<ThemePreference>(readStoredPreference());
  readonly theme = computed<ThemeName>(() => {
    const preference = this.preference();
    if (preference !== 'system') return preference;
    return this.systemLight() ? 'light' : 'dark';
  });

  constructor() {
    const onChange = (event: { matches: boolean }) => this.systemLight.set(event.matches);
    this.query?.addEventListener('change', onChange);
    inject(DestroyRef).onDestroy(() => this.query?.removeEventListener('change', onChange));
    // The page follows the theme whatever changed it: a choice or the device.
    effect(() => document.documentElement.classList.toggle('light', this.theme() === 'light'));
  }

  setPreference(preference: ThemePreference): void {
    try {
      if (preference === 'system') window.localStorage.removeItem(STORAGE_KEY);
      else window.localStorage.setItem(STORAGE_KEY, preference);
    } catch {
      // Storage refused (private mode, full): the choice still holds for this visit.
    }
    this.preference.set(preference);
  }

  /** Switches to the other theme, as an explicit choice. */
  toggle(): void {
    this.setPreference(this.theme() === 'dark' ? 'light' : 'dark');
  }
}
