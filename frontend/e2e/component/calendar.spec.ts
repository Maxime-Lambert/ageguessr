import { expect, test } from '@playwright/test';
import { asGuest } from './session';
import { round, skipRules } from './game-mocks';

/** November 2026, today the 16th: four days in four states, the rest to play or to come. */
const NOVEMBER = {
  today: '2026-11-16',
  month: '2026-11',
  firstMonth: '2026-10',
  days: Array.from({ length: 30 }, (_, index) => {
    const day = index + 1;
    const states: Record<number, string> = { 2: 'UNAVAILABLE', 3: 'STARTED', 4: 'FINISHED' };
    return {
      date: `2026-11-${String(day).padStart(2, '0')}`,
      state: states[day] ?? (day <= 16 ? 'AVAILABLE' : 'UNAVAILABLE'),
    };
  }),
};
const OCTOBER = {
  ...NOVEMBER,
  month: '2026-10',
  days: Array.from({ length: 31 }, (_, index) => ({
    date: `2026-10-${String(index + 1).padStart(2, '0')}`,
    state: 'FINISHED',
  })),
};

test.describe('calendar page', () => {
  test.beforeEach(async ({ page }) => {
    await asGuest(page);
    await page.route('**/api/game/calendar*', (route) =>
      route.fulfill({
        json:
          new URL(route.request().url()).searchParams.get('month') === '2026-10'
            ? OCTOBER
            : NOVEMBER,
      }),
    );
  });

  test('shows each day in its state, with a legend', async ({ page }) => {
    await page.goto('/en/calendar');

    await expect(page.getByRole('heading', { name: 'Calendar', level: 1 })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'November 2026' })).toBeVisible();
    await expect(
      page.getByRole('link', { name: 'Tuesday, November 3, 2026, Started' }),
    ).toBeVisible();
    await expect(
      page.getByRole('link', { name: 'Wednesday, November 4, 2026, Finished' }),
    ).toBeVisible();
    await expect(
      page.getByRole('img', { name: 'Monday, November 2, 2026, No game' }),
    ).toBeVisible();
    await expect(
      page.getByRole('link', { name: /November 16, 2026, To play, today/ }),
    ).toHaveAttribute('href', '/en/');
    await expect(page.getByRole('list', { name: 'Legend' })).toContainText('Started');
  });

  test('goes back a month, no further than the first game', async ({ page }) => {
    await page.goto('/en/calendar');
    await expect(page.getByRole('button', { name: 'Next month' })).toBeDisabled();

    await page.getByRole('button', { name: 'Previous month' }).click();

    await expect(page.getByRole('heading', { name: 'October 2026' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Previous month' })).toBeDisabled();
  });

  test('opens a past day, played on its own date, and leads back', async ({ page }) => {
    await skipRules(page);
    await page.route('**/api/game/days/2026-11-03', (route) =>
      route.fulfill({
        json: {
          date: '2026-11-03',
          rounds: [round(1)],
          score: { points: 0, max: 1000, complete: false },
        },
      }),
    );
    await page.goto('/en/calendar');

    await page.getByRole('link', { name: 'Tuesday, November 3, 2026, Started' }).click();

    await expect(page).toHaveURL(/\/en\/day\/2026-11-03$/);
    await expect(
      page.getByRole('heading', { name: 'Daily game of Tuesday, November 3, 2026' }),
    ).toBeVisible();
    await page.getByRole('link', { name: 'Back to the calendar' }).click();
    await expect(page).toHaveURL(/\/en\/calendar$/);
  });

  test('is a tab of the header', async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 800 });
    await page.goto('/en/calendar');

    await expect(
      page.getByRole('navigation', { name: /main/i }).getByRole('link', { name: 'Calendar' }),
    ).toHaveAttribute('aria-current', 'page');
  });
});
