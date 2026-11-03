import { expect, test } from '@playwright/test';
import { USER, asLoggedIn } from './session';

test.describe('terms banner', () => {
  test('an account on older Terms accepts the new ones from the banner', async ({ page }) => {
    await asLoggedIn(page, { ...USER, termsAccepted: false });
    let sent: unknown = null;
    await page.route('**/api/auth/me/terms', (route) => {
      sent = route.request().postDataJSON();
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ ...USER, termsAccepted: true }),
      });
    });
    await page.goto('/en/');

    const banner = page.getByRole('region', { name: /Our Terms of Use changed on/ });
    await expect(banner).toBeVisible();
    await expect(banner.getByRole('link', { name: 'the new Terms' })).toHaveAttribute(
      'href',
      '/en/terms',
    );

    await banner.getByRole('button', { name: 'Accept' }).click();

    await expect(banner).toBeHidden();
    expect(sent).toEqual({ version: expect.stringMatching(/^\d{4}-\d{2}-\d{2}$/) });
  });

  test.describe('on a phone', () => {
    test.use({ viewport: { width: 375, height: 667 }, hasTouch: true, isMobile: true });

    test('fits the screen', async ({ page }) => {
      await asLoggedIn(page, { ...USER, termsAccepted: false });
      await page.goto('/en/');

      await expect(page.getByRole('button', { name: 'Accept' })).toBeVisible();
      const overflow = await page.evaluate(
        () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
      );
      expect(overflow).toBeLessThanOrEqual(0);
    });
  });
});
