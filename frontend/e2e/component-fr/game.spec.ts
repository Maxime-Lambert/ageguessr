import { expect, test } from '@playwright/test';
import { captureShare, mockGame, mockPlayedDay, sharedTexts } from '../component/game-mocks';

test.describe('le jeu en français', () => {
  test('se joue en français, avec les indices en français par défaut', async ({ page }) => {
    const sent = await mockGame(page);
    await page.goto('/fr/');

    await expect(page.getByRole('heading', { name: 'La partie du jour' })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Manche 1 sur 3' })).toBeVisible();
    // The language of the hints is a preference, in the header's menu.
    await page.getByRole('banner').getByRole('button', { name: 'Préférences' }).click();
    await expect(
      page.getByRole('radiogroup', { name: 'Indices en' }).getByRole('radio', { name: 'Français' }),
    ).toHaveAttribute('aria-checked', 'true');
    await page.keyboard.press('Escape');

    await page.getByLabel('Âge').fill('30');
    await page.getByRole('button', { name: 'Valider' }).click();

    await expect(page.getByText("Plus de 30 ans, de 6 à 15 ans d'écart")).toBeVisible();
    await expect(page.getByText('Entre 36 et 45 ans')).toBeVisible();
    await expect(page.getByText('Choisissez 3 indices')).toBeVisible();
    await expect(page.getByText('Choix : 0 sur 3')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Musique', exact: true })).toBeVisible();
    expect(sent).toEqual([{ guess: 30, language: 'FR' }]);
  });

  test('envoie la langue des indices choisie, sans changer la langue du site', async ({ page }) => {
    const sent = await mockGame(page);
    await page.goto('/fr/');

    const hints = page.getByRole('radiogroup', { name: 'Indices en' });
    await page.getByRole('banner').getByRole('button', { name: 'Préférences' }).click();
    await hints.getByRole('radio', { name: 'English' }).click();
    await page.keyboard.press('Escape');
    await page.getByLabel('Âge').fill('30');
    await page.getByRole('button', { name: 'Valider' }).click();

    await expect(page.getByText(/^Plus de 30 ans/)).toBeVisible();
    expect(sent).toEqual([{ guess: 30, language: 'EN' }]);
    // Remembered on this device.
    await page.reload();
    await page.getByRole('banner').getByRole('button', { name: 'Préférences' }).click();
    await expect(hints.getByRole('radio', { name: 'English' })).toHaveAttribute(
      'aria-checked',
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

  test('partage le résultat du jour avec la date en français', async ({ page, baseURL }) => {
    await captureShare(page);
    await mockPlayedDay(page);
    await page.goto('/fr/');

    await page.getByRole('button', { name: 'Partager mon résultat' }).click();

    await expect
      .poll(() => sharedTexts(page))
      .toEqual([
        `AgeGuessr · Partie du 13/11/2026\u00a0: bien joué\u00a0!\n2340 / 3000 points\n🎯🟩⬛🟥\n🎯 du 1er coup · 🟩 tout près · 🟥 loin · ⬛ photo retirée\nToi aussi, joue sur ${new URL(baseURL!).origin}`,
      ]);
  });
});
