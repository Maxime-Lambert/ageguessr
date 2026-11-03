import { defineConfig, devices } from '@playwright/test';

// One dev server per language (Angular i18n serves one at a time): /en/ on 4200, /fr/ on
// 4201. Tests give the full path, /en/… or /fr/…, as the site does in production.
const baseURL = process.env.E2E_BASE_URL ?? 'http://localhost:4200';
const frenchBaseURL = process.env.E2E_FR_BASE_URL ?? 'http://localhost:4201';

export default defineConfig({
  timeout: 30_000,
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  // Locally, two dev servers plus one Chromium per worker exhaust WSL's memory and crash
  // it: two workers keep the run within budget. CI keeps Playwright's default.
  workers: process.env.CI ? undefined : 2,
  reporter: 'html',
  use: {
    trace: 'on-first-retry',
  },
  webServer: process.env.E2E_BASE_URL
    ? undefined
    : [
        { command: 'pnpm start', url: `${baseURL}/en/`, reuseExistingServer: !process.env.CI },
        {
          command: 'pnpm start:fr',
          url: `${frenchBaseURL}/fr/`,
          reuseExistingServer: !process.env.CI,
        },
      ],
  projects: [
    {
      name: 'component',
      testDir: './e2e/component',
      use: { ...devices['Desktop Chrome'], baseURL },
    },
    {
      name: 'component-fr',
      testDir: './e2e/component-fr',
      use: { ...devices['Desktop Chrome'], baseURL: frenchBaseURL, locale: 'fr-FR' },
    },
    {
      name: 'journeys',
      testDir: './e2e/journeys',
      use: { ...devices['Desktop Chrome'], baseURL },
    },
  ],
});
