import { expect, test } from '@playwright/test';

test('un compte créé depuis le site français est en français', async ({ page }) => {
  let body: unknown;
  await page.route('**/api/auth/register', (route) => {
    body = route.request().postDataJSON();
    return route.fulfill({
      status: 201,
      contentType: 'application/json',
      body: JSON.stringify({ userId: '1', email: 'moi@x.com', username: 'Joueur' }),
    });
  });

  await page.goto('/fr/register');
  await expect(page.getByRole('heading', { name: 'Créez votre compte' })).toBeVisible();
  await page.locator('#email').fill('moi@x.com');
  await page.locator('#username').fill('Joueur');
  await page.locator('#password').fill('tortoise-violin-42');
  await page.locator('#confirmPassword').fill('tortoise-violin-42');
  await page.locator('#acceptTerms').check();
  await page.getByRole('button', { name: 'Créer le compte' }).click();

  await expect(page).toHaveURL(/\/fr\/check-email/);
  await expect(page.getByRole('heading', { name: 'Vérifiez vos emails' })).toBeVisible();
  expect(body).toMatchObject({ email: 'moi@x.com', language: 'FR' });
});
