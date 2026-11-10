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
  await page.route('**/api/friends/invite', (route) =>
    route.fulfill({ json: { code: 'K7F2QX9M' } }),
  );
}

test.describe('friends page', () => {
  test('a guest is sent to log in, and comes back after', async ({ page }) => {
    await asGuest(page);

    await page.goto('/en/friends/invite/K7F2QX9M');

    await expect(page).toHaveURL(/\/en\/login\?returnUrl=%2Ffriends%2Finvite%2FK7F2QX9M$/);
  });

  test('shows the requests, today’s scores, the leaderboard and the invite link', async ({
    page,
  }) => {
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
    await expect(page.locator('[data-invite-link]')).toContainText('/friends/invite/K7F2QX9M');
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
    await expect(page.locator('[data-invite-link]')).toBeVisible();

    const overflow = await page.evaluate(
      () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
    );
    expect(overflow).toBeLessThanOrEqual(0);
  });

  test('opens a friend’s invite link and adds them', async ({ page }) => {
    await asLoggedIn(page);
    await page.route('**/api/friends/invite/K7F2QX9M', (route) =>
      route.fulfill({ json: { username: 'Holmes', own: false, alreadyFriends: false } }),
    );
    await page.route('**/api/friends/invite/K7F2QX9M/accept', (route) =>
      route.fulfill({ status: 204 }),
    );

    await page.goto('/en/friends/invite/K7F2QX9M');
    await expect(page.getByText('Holmes invites you to be friends on AgeGuessr.')).toBeVisible();
    await page.getByRole('button', { name: 'Add as a friend' }).click();

    await expect(page.getByText('You and Holmes are now friends.')).toBeVisible();
  });
});
