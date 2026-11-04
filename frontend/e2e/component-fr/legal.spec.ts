import { expect, test } from '@playwright/test';
import { asGuest } from '../component/session';

const PAGES = [
  { path: '/legal-notice', fr: 'Mentions légales' },
  { path: '/terms', fr: "Conditions générales d'utilisation" },
  { path: '/privacy', fr: 'Politique de confidentialité' },
];

test.describe('pages légales en français', () => {
  for (const { path, fr } of PAGES) {
    test(`${path} est en français et tient sur un téléphone`, async ({ page }) => {
      await asGuest(page);
      await page.setViewportSize({ width: 360, height: 740 });
      await page.goto(`/fr${path}`);

      await expect(page.getByRole('heading', { level: 1 })).toHaveText(fr);
      await expect(page.locator('html')).toHaveAttribute('lang', 'fr');
      const overflow = await page.evaluate(
        () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
      );
      expect(overflow).toBe(0);
      // On a phone, the site language is in the menu panel.
      await page.getByRole('button', { name: 'Menu' }).click();
      await expect(
        page.getByRole('dialog', { name: 'Menu' }).getByRole('link', { name: 'English' }),
      ).toHaveAttribute('href', `/en${path}`);
    });
  }

  test('le pied de page mène aux pages légales françaises', async ({ page }) => {
    await asGuest(page);
    await page.goto('/fr/login');

    await page
      .getByRole('navigation', { name: 'Informations légales' })
      .getByRole('link', { name: 'Confidentialité' })
      .click();

    await expect(page).toHaveURL(/\/fr\/privacy$/);
  });
});
