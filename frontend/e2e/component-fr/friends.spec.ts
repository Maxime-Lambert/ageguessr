import { expect, test } from '@playwright/test';
import { USER, asLoggedIn } from '../component/session';

test.describe('amis', () => {
  test('la page des amis est en français', async ({ page }) => {
    await asLoggedIn(page, { ...USER, language: 'FR' });
    await page.route('**/api/friends', (route) =>
      route.fulfill({
        json: {
          friends: [],
          incoming: [{ id: 'r1', username: 'adler', since: '2026-11-09T10:00:00Z' }],
          outgoing: [],
        },
      }),
    );
    await page.route('**/api/friends/today', (route) =>
      route.fulfill({ json: { date: '2026-11-10', locked: true, players: [] } }),
    );
    await page.route('**/api/friends/leaderboard', (route) =>
      route.fulfill({
        json: {
          players: [
            {
              rank: null,
              username: 'Player',
              me: true,
              gamesPlayed: 0,
              averagePoints: null,
              currentStreak: 0,
            },
          ],
        },
      }),
    );
    await page.goto('/fr/friends');

    await expect(page.getByRole('heading', { name: 'Demandes reçues (1)' })).toBeVisible();
    await expect(
      page.getByText('Terminez le dossier du jour pour voir les scores de vos amis.'),
    ).toBeVisible();
    await expect(page.getByText('Aucun dossier')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Envoyer la demande' })).toBeVisible();
    await expect(page.getByText("Pas encore d'ami.")).toBeVisible();
  });
});
