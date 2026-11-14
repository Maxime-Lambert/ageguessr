import { expect, test } from '@playwright/test';
import { asGuest, asLoggedIn } from './session';

const HISTORY = {
  stats: {
    gamesPlayed: 12,
    averagePoints: 1820,
    bestPoints: 2750,
    currentStreak: 4,
    bestStreak: 9,
  },
  days: [
    {
      date: '2026-11-08',
      points: 1734,
      max: 3000,
      complete: true,
      rounds: [
        {
          position: 1,
          finished: true,
          neutralized: false,
          name: 'Jane Doe',
          ageAtPhoto: 52,
          firstGuess: 45,
          secondGuess: 50,
          points: 734,
        },
        {
          position: 2,
          finished: true,
          neutralized: true,
          name: null,
          ageAtPhoto: null,
          firstGuess: 30,
          secondGuess: null,
          points: 1000,
        },
      ],
    },
  ],
  page: 0,
  hasMore: false,
};

test.describe('profile page', () => {
  test('a guest is sent to log in, and comes back after', async ({ page }) => {
    await asGuest(page);

    await page.goto('/en/profile');

    await expect(page).toHaveURL(/\/en\/login\?returnUrl=%2Fprofile$/);
  });

  test('shows the streak, the statistics and the past files', async ({ page }) => {
    await asLoggedIn(page);
    await page.route('**/api/game/history*', (route) => route.fulfill({ json: HISTORY }));

    await page.goto('/en/profile');

    await expect(page.getByRole('heading', { name: 'Player' })).toBeVisible();
    await expect(page.locator('section[aria-labelledby="streak-title"]')).toContainText('4');
    await expect(page.getByText('Average score')).toBeVisible();
    const day = page.getByRole('article', { name: 'Sunday, November 8, 2026' });
    await expect(day).toContainText('1,734 out of 3,000');
    await expect(day).toContainText('Jane Doe');
    await expect(day).toContainText('Photo removed');
    await expect(day.locator('img')).toHaveCount(0);
  });

  test('is reached from the account menu', async ({ page }) => {
    await asLoggedIn(page);
    await page.route('**/api/game/history*', (route) => route.fulfill({ json: HISTORY }));
    await page.goto('/en/');

    await page.getByRole('banner').getByRole('button', { name: 'Player' }).click();
    await page.getByRole('link', { name: 'My profile' }).click();

    await expect(page).toHaveURL(/\/en\/profile$/);
  });

  test('fits a phone screen without scrolling sideways', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 });
    await asLoggedIn(page);
    await page.route('**/api/game/history*', (route) => route.fulfill({ json: HISTORY }));

    await page.goto('/en/profile');
    await expect(page.getByRole('article')).toBeVisible();

    const overflow = await page.evaluate(
      () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
    );
    expect(overflow).toBeLessThanOrEqual(0);
  });
});
