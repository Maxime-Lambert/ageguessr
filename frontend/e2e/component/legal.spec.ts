import { expect, test } from '@playwright/test';
import { asGuest } from './session';

const PAGES = [
  { path: '/legal-notice', footerLink: 'Legal notice', en: 'Legal notice', fr: 'Mentions légales' },
  {
    path: '/terms',
    footerLink: 'Terms',
    en: 'Terms of Use',
    fr: "Conditions générales d'utilisation",
  },
  {
    path: '/privacy',
    footerLink: 'Privacy',
    en: 'Privacy Policy',
    fr: 'Politique de confidentialité',
  },
];

test.describe('legal pages', () => {
  for (const { path, footerLink, en, fr } of PAGES) {
    test(`${path} is reachable from the footer and switches language`, async ({ page }) => {
      await asGuest(page);
      await page.goto('/');

      await page
        .getByRole('navigation', { name: 'Legal' })
        .getByRole('link', { name: footerLink, exact: true })
        .click();
      await expect(page).toHaveURL(new RegExp(`${path}$`));
      await expect(page.getByRole('heading', { level: 1 })).toHaveText(en);

      await page.getByRole('link', { name: 'Version française' }).click();
      await expect(page).toHaveURL(new RegExp(`${path}\\?lang=fr$`));
      await expect(page.getByRole('heading', { level: 1 })).toHaveText(fr);

      await page.getByRole('link', { name: 'English version' }).click();
      await expect(page.getByRole('heading', { level: 1 })).toHaveText(en);
    });

    test(`${path} fits a phone screen without horizontal scrolling`, async ({ page }) => {
      await asGuest(page);
      await page.setViewportSize({ width: 360, height: 740 });

      for (const url of [path, `${path}?lang=fr`]) {
        await page.goto(url);
        await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
        const overflow = await page.evaluate(
          () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
        );
        expect(overflow, url).toBe(0);
      }
    });
  }

  test('the footer stays at the bottom of the screen on a short page', async ({ page }) => {
    await asGuest(page);
    await page.setViewportSize({ width: 1280, height: 900 });
    await page.goto('/login');

    const footer = await page.getByRole('contentinfo').boundingBox();
    expect(footer!.y + footer!.height).toBeCloseTo(900, 0);
  });
});
