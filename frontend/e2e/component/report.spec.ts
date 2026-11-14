import { Page, expect, test } from '@playwright/test';
import { mockGame, pickLeads } from './game-mocks';

/** Plays the first round of the mocked day to its verdict. */
async function toTheVerdict(page: Page) {
  await page.getByLabel('Age').fill('30');
  await page.getByRole('button', { name: 'Guess', exact: true }).click();
  await pickLeads(page);
  await page.getByLabel('Age').fill('38');
  await page.getByRole('button', { name: 'Final guess' }).click();
  await expect(page.getByRole('link', { name: 'Jane Doe' })).toBeVisible();
}

test.describe('reports from the verdict', () => {
  test('reports the photo with a reason from the list', async ({ page }) => {
    const sent = await mockGame(page);
    await page.goto('/en/');
    await toTheVerdict(page);

    await page.getByRole('button', { name: 'Report this photo' }).click();
    const send = page.getByRole('button', { name: 'Send' });
    await expect(send).toBeDisabled();
    await page.getByLabel('It is not the right person').check();
    await send.click();

    await expect(page.getByText('Reported, thank you. We will take a look.')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Report this photo' })).toHaveCount(0);
    // The hint keeps its own report, with its own reasons.
    await page.getByRole('button', { name: 'Report this hint' }).click();
    await expect(page.getByLabel('The year is wrong')).toBeVisible();
    expect(sent.at(-1)).toEqual({ target: 'PHOTO', reason: 'WRONG_PERSON' });
  });

  test.describe('on a phone', () => {
    test.use({ viewport: { width: 375, height: 667 }, hasTouch: true, isMobile: true });

    test('the list of reasons fits the screen', async ({ page }) => {
      await mockGame(page);
      await page.goto('/en/');
      await toTheVerdict(page);

      await page.getByRole('button', { name: 'Report this photo' }).click();
      await expect(page.getByLabel('The person looks under 18')).toBeVisible();
      const overflow = await page.evaluate(
        () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
      );
      expect(overflow).toBeLessThanOrEqual(0);
    });
  });
});
