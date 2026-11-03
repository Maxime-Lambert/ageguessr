import { expect, test } from '@playwright/test';
import { USER, asLoggedIn } from '../component/session';

test.describe('bandeau des conditions', () => {
  test('invite en français à accepter les nouvelles conditions', async ({ page }) => {
    await asLoggedIn(page, { ...USER, language: 'FR', termsAccepted: false });
    await page.goto('/fr/');

    const banner = page.getByRole('region', { name: /Nos conditions d'utilisation ont changé le/ });
    await expect(banner).toBeVisible();
    await expect(banner.getByRole('link', { name: 'les nouvelles conditions' })).toHaveAttribute(
      'href',
      '/fr/terms',
    );
    await banner.getByRole('button', { name: 'Plus tard' }).click();
    await expect(banner).toBeHidden();
  });
});
