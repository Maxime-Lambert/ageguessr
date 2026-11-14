import { expect, test } from '@playwright/test';
import { mockGame, pickLeads } from '../component/game-mocks';

test.describe('signalements en français', () => {
  test("signale l'indice avec un motif traduit", async ({ page }) => {
    const sent = await mockGame(page);
    await page.goto('/fr/');

    await page.getByLabel('Âge').fill('30');
    await page.getByRole('button', { name: 'Valider' }).click();
    await pickLeads(page, ['Musique', 'Cinéma', 'Sport'], 'Voir ces indices');
    await page.getByLabel('Âge').fill('38');
    await page.getByRole('button', { name: 'Dernier essai' }).click();
    await expect(page.getByRole('link', { name: 'Jane Doe' })).toBeVisible();

    await page.getByRole('button', { name: 'Signaler cet indice' }).click();
    await page.getByLabel("L'année est fausse").check();
    await page.getByRole('button', { name: 'Envoyer' }).click();

    await expect(page.getByText('Signalé, merci. Nous allons regarder.')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Signaler cette photo' })).toBeVisible();
    expect(sent.at(-1)).toEqual({ target: 'HINT', reason: 'WRONG_YEAR', hintCategory: 'MUSIC' });
  });
});
