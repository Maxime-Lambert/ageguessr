import { expect, test } from '@playwright/test';
import { USER, asLoggedIn } from '../component/session';

test.describe('profil', () => {
  test('affiche la série, les statistiques et les dossiers en français', async ({ page }) => {
    await asLoggedIn(page, { ...USER, language: 'FR' });
    await page.route('**/api/game/history*', (route) =>
      route.fulfill({
        json: {
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
              complete: false,
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
              ],
            },
          ],
          page: 0,
          hasMore: true,
        },
      }),
    );

    await page.goto('/fr/profile');

    await expect(page.getByRole('heading', { name: 'Série en cours' })).toBeVisible();
    await expect(page.getByText('Score moyen')).toBeVisible();
    const day = page.getByRole('article', { name: 'dimanche 8 novembre 2026' });
    // Espaces fines insécables : le format des nombres en français.
    await expect(day).toContainText(/1 734 sur 3 000/);
    await expect(day).toContainText('Jane Doe · 52 ans');
    await expect(day).toContainText('Vos essais : 45 → 50');
    await expect(day).toContainText('Pas terminé');
    await expect(page.getByRole('button', { name: 'Dossiers plus anciens' })).toBeVisible();
  });
});
