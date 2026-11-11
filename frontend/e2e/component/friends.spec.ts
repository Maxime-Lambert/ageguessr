import { Page, expect, test } from '@playwright/test';
import { asGuest, asLoggedIn } from './session';

const FRIENDS = {
  friends: [{ id: 'f1', username: 'watson', since: '2026-11-01T10:00:00Z' }],
  incoming: [{ id: 'r1', username: 'adler', since: '2026-11-09T10:00:00Z' }],
  outgoing: [{ id: 'r2', username: 'moriarty', since: '2026-11-08T10:00:00Z' }],
};

const TODAY = {
  date: '2026-11-10',
  locked: false,
  players: [
    {
      username: 'watson',
      me: false,
      status: 'COMPLETE',
      points: 2600,
      max: 3000,
      rounds: [
        { position: 1, points: 950 },
        { position: 2, points: 650 },
        { position: 3, points: 1000 },
      ],
    },
    {
      username: 'Player',
      me: true,
      status: 'COMPLETE',
      points: 1800,
      max: 3000,
      rounds: [
        { position: 1, points: 300 },
        { position: 2, points: 500 },
        { position: 3, points: 1000 },
      ],
    },
  ],
};

const BOARD = {
  players: [
    {
      rank: 1,
      username: 'watson',
      me: false,
      gamesPlayed: 9,
      averagePoints: 2400,
      currentStreak: 3,
    },
    {
      rank: 2,
      username: 'Player',
      me: true,
      gamesPlayed: 4,
      averagePoints: 2100,
      currentStreak: 2,
    },
  ],
};

async function withFriends(page: Page): Promise<void> {
  await asLoggedIn(page);
  await page.route('**/api/friends', (route) => route.fulfill({ json: FRIENDS }));
  await page.route('**/api/friends/today', (route) => route.fulfill({ json: TODAY }));
  await page.route('**/api/friends/leaderboard', (route) => route.fulfill({ json: BOARD }));
}

test.describe('friends page', () => {
  test('a guest is sent to log in, and comes back after', async ({ page }) => {
    await asGuest(page);

    await page.goto('/en/friends');

    await expect(page).toHaveURL(/\/en\/login\?returnUrl=%2Ffriends$/);
  });

  test('shows the requests, today’s scores and the leaderboard', async ({ page }) => {
    await withFriends(page);

    await page.goto('/en/friends');

    await expect(page.getByRole('heading', { name: 'Requests received (1)' })).toBeVisible();
    await expect(page.locator('[data-incoming]')).toContainText('adler');
    await expect(page.locator('section[aria-labelledby="friends-today-title"]')).toContainText(
      '2,600 out of 3,000',
    );
    await expect(page.locator('section[aria-labelledby="friends-board-title"]')).toContainText(
      'watson',
    );
    await expect(page.locator('[data-friend]')).toContainText('watson');
    await expect(page.locator('[data-outgoing]')).toContainText('moriarty');
  });

  test('is reached from the account menu, with the count of requests', async ({ page }) => {
    await withFriends(page);
    await page.goto('/en/');

    await page.getByRole('banner').getByRole('button', { name: 'Player' }).click();
    const link = page.getByRole('link', { name: /Friends/ });
    await expect(link).toContainText('1');
    await link.click();

    await expect(page).toHaveURL(/\/en\/friends$/);
  });

  test('fits a phone without scrolling sideways', async ({ page }) => {
    await page.setViewportSize({ width: 360, height: 780 });
    await withFriends(page);

    await page.goto('/en/friends');
    await expect(page.locator('[data-friend]')).toBeVisible();

    const overflow = await page.evaluate(
      () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
    );
    expect(overflow).toBeLessThanOrEqual(0);
  });

  test('sends an invite link shared before their removal to the friends page', async ({ page }) => {
    await withFriends(page);

    await page.goto('/en/friends/invite/K7F2QX9M');

    await expect(page).toHaveURL(/\/en\/friends$/);
  });
});
