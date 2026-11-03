import { expect, test } from '@playwright/test';
import { mockGame } from '../component/game-mocks';

test.describe('le jeu en français', () => {
  test('se joue en français, avec les indices en français par défaut', async ({ page }) => {
    const sent = await mockGame(page);
    await page.goto('/fr/');

    await expect(page.getByRole('heading', { name: 'Le dossier du jour' })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Affaire 1 sur 3' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Français' })).toHaveAttribute(
      'aria-pressed',
      'true',
    );

    await page.getByLabel('Âge').fill('30');
    await page.getByRole('button', { name: 'Valider' }).click();

    await expect(page.getByText('Plus de 30 ans')).toBeVisible();
    await expect(page.getByText('Choisissez une piste')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Musique' })).toBeVisible();
    expect(sent).toEqual([{ guess: 30, language: 'FR' }]);
  });

  test('envoie la langue des indices choisie, sans changer la langue du site', async ({ page }) => {
    const sent = await mockGame(page);
    await page.goto('/fr/');

    await page.getByRole('button', { name: 'English' }).click();
    await page.getByLabel('Âge').fill('30');
    await page.getByRole('button', { name: 'Valider' }).click();

    await expect(page.getByText('Plus de 30 ans')).toBeVisible();
    expect(sent).toEqual([{ guess: 30, language: 'EN' }]);
    // Remembered on this device.
    await page.reload();
    await expect(page.getByRole('button', { name: 'English' })).toHaveAttribute(
      'aria-pressed',
      'true',
    );
  });

  test('refuse un âge hors limites, en français', async ({ page }) => {
    await mockGame(page);
    await page.goto('/fr/');

    await page.getByLabel('Âge').fill('12');
    await page.getByRole('button', { name: 'Valider' }).click();

    await expect(page.getByText('Saisissez un âge entier entre 18 et 100.')).toBeVisible();
  });
});
