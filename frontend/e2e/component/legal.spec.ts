import { expect, test } from '@playwright/test';
import { asGuest } from './session';

const PAGES = [
  { path: '/legal-notice', footerLink: 'Legal notice', en: 'Legal notice' },
  { path: '/terms', footerLink: 'Terms', en: 'Terms of Use' },
  { path: '/privacy', footerLink: 'Privacy', en: 'Privacy Policy' },
];

test.describe('legal pages', () => {
  for (const { path, footerLink, en } of PAGES) {
    test(`${path} is reachable from the footer and links to its French version`, async ({
      page,
      context,
    }) => {
      await asGuest(page);
      // The French build is another dev server (e2e/component-fr): stand in for it here.
      await page.route('**/fr/**', (route) =>
        route.fulfill({ status: 200, contentType: 'text/html', body: '<h1>fr</h1>' }),
      );
      await page.goto('/en/');

      await page
        .getByRole('navigation', { name: 'Legal' })
        .getByRole('link', { name: footerLink, exact: true })
        .click();
      await expect(page).toHaveURL(new RegExp(`/en${path}$`));
      await expect(page.getByRole('heading', { level: 1 })).toHaveText(en);

      const french = page.getByRole('banner').getByRole('link', { name: 'Français' });
      await expect(french).toHaveAttribute('href', `/fr${path}`);
      await expect(french).toHaveAttribute('hreflang', 'fr');
      await french.click();

      await expect(page).toHaveURL(new RegExp(`/fr${path}$`));
      const cookies = await context.cookies();
      expect(cookies.find((cookie) => cookie.name === 'ageguessr_lang')?.value).toBe('fr');
    });

    test(`${path} fits a phone screen without horizontal scrolling`, async ({ page }) => {
      await asGuest(page);
      await page.setViewportSize({ width: 360, height: 740 });

      await page.goto(`/en${path}`);
      await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
      const overflow = await page.evaluate(
        () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
      );
      expect(overflow).toBe(0);
    });
  }

  test('the footer stays at the bottom of the screen on a short page', async ({ page }) => {
    await asGuest(page);
    await page.setViewportSize({ width: 1280, height: 900 });
    await page.goto('/en/login');

    const footer = await page.getByRole('contentinfo').boundingBox();
    expect(footer!.y + footer!.height).toBeCloseTo(900, 0);
  });
});
