import { expect, test } from '@playwright/test';
import { asGuest } from '../component/session';

test.describe('calendrier', () => {
  test('nomme le mois, les jours et leur état en français', async ({ page }) => {
    await asGuest(page);
    await page.route('**/api/game/calendar*', (route) =>
      route.fulfill({
        json: {
          today: '2026-11-16',
          month: '2026-11',
          firstMonth: '2026-11',
          days: [
            { date: '2026-11-01', state: 'FINISHED' },
            { date: '2026-11-02', state: 'STARTED' },
          ],
        },
      }),
    );

    await page.goto('/fr/calendar');

    await expect(page.getByRole('heading', { name: 'Calendrier', level: 1 })).toBeVisible();
    await expect(page.getByRole('heading', { name: /novembre 2026/i })).toBeVisible();
    await expect(
      page.getByRole('link', { name: 'dimanche 1 novembre 2026, Terminée' }),
    ).toBeVisible();
    await expect(
      page.getByRole('link', { name: 'lundi 2 novembre 2026, Commencée' }),
    ).toBeVisible();
  });
});
