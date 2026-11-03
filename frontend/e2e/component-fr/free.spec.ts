import { expect, test } from '@playwright/test';
import { mockChallenge, mockSeries } from '../component/game-mocks';

test.describe('enquête libre en français', () => {
  test('se choisit et se joue en français', async ({ page }) => {
    const started = await mockSeries(page);
    await page.goto('/fr/free');

    await expect(page.getByRole('heading', { name: 'Enquête libre', level: 1 })).toBeVisible();
    await expect(page.getByText("Combien d'affaires ?")).toBeVisible();
    await page.getByRole('button', { name: '5 affaires' }).click();

    await expect(page.getByRole('heading', { name: 'Affaire 1 sur 5' })).toBeVisible();
    expect(started).toEqual([{ size: 5 }]);
  });

  test('un défi se relève en français', async ({ page }) => {
    const joined = await mockChallenge(page);
    await page.goto('/fr/challenge/K7F2QX9M');

    await expect(page.getByText('Sherlock vous défie sur 5 affaires.')).toBeVisible();
    await page.getByLabel('Votre nom dans le défi').fill('Watson');
    await page.getByRole('button', { name: 'Relever le défi' }).click();

    await expect(page.getByRole('heading', { name: 'Affaire 1 sur 5' })).toBeVisible();
    expect(joined).toEqual([{ nickname: 'Watson' }]);
  });
});
