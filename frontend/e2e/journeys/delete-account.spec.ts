import { expect, test } from '@playwright/test';
import { lastEmailToken, logIn, signUp, uniqueAccount } from './support';

test('request deletion, confirm through the emailed link, the account is gone', async ({
  page,
  request,
}) => {
  const account = uniqueAccount('delete');
  await signUp(page, request, account);
  await logIn(page, account.email, account.password);
  await expect(page).toHaveURL('/');

  await page.goto('/account');
  await page.locator('#deletionPassword').fill(account.password);
  await page.getByRole('button', { name: 'Delete my account' }).click();
  await expect(page.getByText('We sent a confirmation link to')).toBeVisible();

  // Nothing deleted yet: still logged in after a reload.
  await page.reload();
  await expect(page.getByRole('heading', { name: 'Your account' })).toBeVisible();

  await page.goto(
    `/confirm-account-deletion?token=${await lastEmailToken(request, account.email)}`,
  );
  await page.getByRole('button', { name: 'Delete my account permanently' }).click();
  await expect(page.getByRole('heading', { name: 'Account deleted' })).toBeVisible();
  await expect(page.getByRole('banner').getByRole('link', { name: 'Log in' })).toBeVisible();

  await logIn(page, account.email, account.password);
  await expect(page.getByText('Invalid email or password.')).toBeVisible();
});
