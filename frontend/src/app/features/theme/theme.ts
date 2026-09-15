import { Service, signal } from '@angular/core';

export type ThemeName = 'dark' | 'light';

const STORAGE_KEY = 'ageguessr-theme';

function readStoredTheme(): ThemeName {
  const stored = window.localStorage.getItem(STORAGE_KEY);
  return stored === 'light' ? 'light' : 'dark';
}

function applyTheme(theme: ThemeName) {
  document.documentElement.classList.toggle('light', theme === 'light');
}

@Service()
export class Theme {
  readonly theme = signal<ThemeName>(readStoredTheme());

  constructor() {
    applyTheme(this.theme());
  }

  setTheme(theme: ThemeName): void {
    window.localStorage.setItem(STORAGE_KEY, theme);
    applyTheme(theme);
    this.theme.set(theme);
  }

  toggle(): void {
    this.setTheme(this.theme() === 'dark' ? 'light' : 'dark');
  }
}
