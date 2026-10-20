import { expect, test } from '@playwright/test';
import { asGuest } from './session';

test.describe('reset-password page', () => {
  test('sets a new password from the emailed link', async ({ page }) => {
    await asGuest(page);
    let sentBody: unknown;
    await page.route('**/api/auth/reset-password', (route) => {
      sentBody = route.request().postDataJSON();
      return route.fulfill({ status: 204, body: '' });
    });

    await page.goto('/reset-password?token=the-token');
    await page.locator('#newPassword').fill('new-tortoise-violin');
    await page.locator('#confirmPassword').fill('new-tortoise-violin');
    await page.getByRole('button', { name: 'Set new password' }).click();

    await expect(page.getByRole('heading', { name: 'Password updated' })).toBeVisible();
    expect(sentBody).toEqual({ token: 'the-token', newPassword: 'new-tortoise-violin' });
  });

  test('offers a new link when this one is expired', async ({ page }) => {
    await asGuest(page);
    await page.route('**/api/auth/reset-password', (route) =>
      route.fulfill({ status: 404, contentType: 'application/json', body: '{}' }),
    );

    await page.goto('/reset-password?token=expired');
    await page.locator('#newPassword').fill('new-tortoise-violin');
    await page.locator('#confirmPassword').fill('new-tortoise-violin');
    await page.getByRole('button', { name: 'Set new password' }).click();

    await expect(page.getByRole('link', { name: 'Get a new link' })).toBeVisible();
  });
});
