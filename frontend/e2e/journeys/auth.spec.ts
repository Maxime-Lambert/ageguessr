import { expect, test } from '@playwright/test';
import { logIn, signUp, uniqueAccount } from './support';

test('sign up, confirm, log in, rename, log out', async ({ page, request }) => {
  const account = uniqueAccount('journey');
  await signUp(page, request, account);

  await logIn(page, account.email, account.password);
  await expect(page).toHaveURL('/en/');
  const header = page.getByRole('banner');
  await expect(header.getByRole('button', { name: account.username })).toBeVisible();

  // The session survives a hard reload (HttpOnly refresh cookie), even on an
  // account-only page.
  await page.goto('/en/account');
  await expect(page.getByRole('heading', { name: 'Your account' })).toBeVisible();

  const newUsername = `${account.username.slice(0, 17)}new`;
  await page.locator('#username').fill(newUsername);
  await page.getByRole('button', { name: 'Save username' }).click();
  await expect(page.getByText('Username updated.')).toBeVisible();
  await expect(header.getByRole('button', { name: newUsername })).toBeVisible();

  await header.getByRole('button', { name: newUsername }).click();
  await header.getByRole('button', { name: 'Log out' }).click();
  await expect(header.getByRole('link', { name: 'Log in' })).toBeVisible();
  await page.goto('/en/account');
  await expect(page).toHaveURL(/\/en\/login\?returnUrl=%2Faccount$/);
});
