import { expect, test } from '@playwright/test';
import { openLastEmailLink, uniqueAccount } from './support';

test('a player signs up on the French site and gets French emails, with French links', async ({
  page,
  request,
}) => {
  const account = uniqueAccount('fr');

  await page.goto(`${process.env.E2E_FR_BASE_URL ?? 'http://localhost:4201'}/fr/register`);
  await page.locator('#email').fill(account.email);
  await page.locator('#username').fill(account.username);
  await page.locator('#password').fill(account.password);
  await page.locator('#confirmPassword').fill(account.password);
  await page.locator('#acceptTerms').check();
  await page.getByRole('button', { name: 'Créer le compte' }).click();
  await expect(page).toHaveURL(/\/fr\/check-email/);

  // The account was saved in French: the email is French and its link opens the French site.
  const email = await openLastEmailLink(page, request, account.email);
  expect(email.subject).toBe('Confirmez votre compte Ageguessr');
  expect(email.link.pathname).toBe('/fr/confirm-email');
  await expect(page.getByRole('heading', { name: 'Email confirmé' })).toBeVisible();

  // Logging in on the French site stays there: the account's language is already French.
  await page.getByRole('banner').getByRole('link', { name: 'Se connecter' }).click();
  await page.locator('#email').fill(account.email);
  await page.locator('#password').fill(account.password);
  await page.locator('form').getByRole('button', { name: 'Se connecter' }).click();
  await expect(page).toHaveURL(/\/fr\/$/);
  await expect(
    page.getByRole('banner').getByRole('button', { name: account.username }),
  ).toBeVisible();
});
